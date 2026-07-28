package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.dto.AiChatRequest;
import com.bulongyu.housing.vo.AiChatResponse;
import com.bulongyu.housing.vo.AiConversationView;
import com.bulongyu.housing.vo.AiMessageView;


import com.bulongyu.housing.entity.AiConversation;
import com.bulongyu.housing.entity.AiConversationContext;
import com.bulongyu.housing.entity.AiMessage;
import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.mapper.AiMapper;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 客服业务服务
 */
@Service
public class AiConversationService {
    private final AiMapper mapper;
    private final UserMapper users;
    private final AiOrchestrator orchestrator;
    private final AiConversationContextService contextService;
    private final ObjectMapper json;

    /**
     * 初始化 {@code AiConversationService} 并注入所需依赖。
     *
     * @param mapper 数据访问组件
     * @param users 用户数据访问组件
     * @param orchestrator AI 请求编排服务
     * @param json JSON 序列化组件
     */
    public AiConversationService(AiMapper mapper,
                                 UserMapper users,
                                 AiOrchestrator orchestrator,
                                 AiConversationContextService contextService,
                                 ObjectMapper json) {
        this.mapper = mapper;
        this.users = users;
        this.orchestrator = orchestrator;
        this.contextService = contextService;
        this.json = json;
    }

    /**
     * 处理一次 AI 客服对话并保存用户消息与助手回复。
     *
     * @param authUserId 当前登录用户编号
     * @param request 请求参数
     * @param requestId 请求追踪编号
     */
    @Transactional
    public AiChatResponse chat(Long authUserId, AiChatRequest request, String requestId) {
        UserProfile profile = profile(authUserId);
        String normalizedMessage = request.message().trim();

        // 1. 解析并校验会话归属，防止用户通过 conversationId 读取或写入他人会话。
        AiConversation conversation = resolveConversation(profile.id(), request);

        // 2. 最近消息按数据库返回顺序取出后反转，为模型恢复从旧到新的对话上下文。
        List<AiMessage> recentMessages = mapper.findRecentMessages(conversation.id(), 10);
        Collections.reverse(recentMessages);
        List<AiModelGateway.ChatTurn> history = recentMessages.stream()
                .map(this::toChatTurn)
                .toList();
        AiConversationContext conversationContext = contextService.resolve(
                recentMessages, request.houseId(), normalizedMessage);

        // 3. 编排器先完成意图路由和结果生成；失败时不保存不完整的对话。
        AgentContext agentContext = new AgentContext(authUserId, conversation.id(), requestId);
        AiOrchestrator.Result result = orchestrator.answer(
                agentContext,
                normalizedMessage,
                history,
                AgentToolEventListener.NO_OP,
                conversationContext.currentHouseId());
        LocalDateTime now = LocalDateTime.now();

        // 4. 用户消息、助手消息和会话标题在同一事务中按顺序写入，任一步失败都会整体回滚。
        insertMessage(conversation.id(), "user", normalizedMessage, userMetadata(request.houseId()), now);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", result.type());
        if (result.retrievalStatus() != null) {
            metadata.put("retrieval_status", result.retrievalStatus());
        }
        metadata.put("houses", result.houses());
        metadata.put("sources", result.sources());
        metadata.put("pending_actions", result.pendingActions());
        metadata.put("tool_calls", result.toolTraces());
        metadata.put(contextService.metadataKey(), contextService.afterResponse(conversationContext, result));
        insertMessage(conversation.id(), "assistant", result.response(), write(metadata), now);

        String title = "新对话".equals(conversation.title())
                ? normalizedMessage.substring(0, Math.min(50, normalizedMessage.length()))
                : conversation.title();
        mapper.updateConversation(conversation.id(), title, now);
        return new AiChatResponse(result.response(), result.type(), result.houses(), result.sources(),
                result.pendingActions(), result.retrievalStatus(), conversation.id(), requestId);
    }

    /**
     * 在短事务中校验会话并保存流式请求的用户消息。
     *
     * @param authUserId 当前认证用户编号
     * @param request AI 对话请求
     * @param requestId 请求追踪编号
     * @return 流式生成所需的不可变上下文
     */
    @Transactional
    public PreparedRun prepareStream(Long authUserId,
                                     AiChatRequest request,
                                     String requestId) {
        UserProfile profile = profile(authUserId);
        String normalizedMessage = request.message().trim();
        AiConversation conversation = resolveConversation(profile.id(), request);
        List<AiMessage> recentMessages = mapper.findRecentMessages(conversation.id(), 10);
        Collections.reverse(recentMessages);
        List<AiModelGateway.ChatTurn> history = recentMessages.stream()
                .map(this::toChatTurn)
                .toList();
        AiConversationContext conversationContext = contextService.resolve(
                recentMessages, request.houseId(), normalizedMessage);
        Long userMessageId = insertMessage(
                conversation.id(), "user", normalizedMessage, userMetadata(request.houseId()), LocalDateTime.now());
        return new PreparedRun(
                UUID.randomUUID().toString(),
                authUserId,
                profile.id(),
                conversation.id(),
                conversation.title(),
                normalizedMessage,
                conversationContext.currentHouseId(),
                requestId,
                history,
                conversationContext,
                userMessageId);
    }

    /**
     * 在短事务中保存完整助手回复、执行摘要并更新会话标题。
     *
     * @param run 流式生成上下文
     * @param result 编排层完整结果
     * @return 持久化完成信息
     */
    @Transactional
    public StreamCompletion completeStream(PreparedRun run, AiOrchestrator.Result result) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("run_id", run.runId());
        metadata.put("status", "completed");
        metadata.put("type", result.type());
        if (result.retrievalStatus() != null) {
            metadata.put("retrieval_status", result.retrievalStatus());
        }
        metadata.put("houses", result.houses());
        metadata.put("sources", result.sources());
        metadata.put("pending_actions", result.pendingActions());
        metadata.put("tool_calls", result.toolTraces());
        metadata.put(contextService.metadataKey(), contextService.afterResponse(run.conversationContext(), result));
        Long assistantMessageId = insertMessage(
                run.conversationId(),
                "assistant",
                result.response(),
                write(metadata),
                now);
        String title = "新对话".equals(run.originalTitle())
                ? run.query().substring(0, Math.min(50, run.query().length()))
                : run.originalTitle();
        mapper.updateConversation(run.conversationId(), title, now);
        AiChatResponse response = new AiChatResponse(
                result.response(),
                result.type(),
                result.houses(),
                result.sources(),
                result.pendingActions(),
                result.retrievalStatus(),
                run.conversationId(),
                run.requestId());
        return new StreamCompletion(assistantMessageId, response);
    }

    /**
     * 在生成失败或客户端取消时记录明确状态，避免把未完成请求伪装为成功回答。
     *
     * @param run 流式生成上下文
     * @param status failed 或 cancelled
     * @param errorCode 安全错误码
     */
    @Transactional
    public void failStream(PreparedRun run, String status, String errorCode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("run_id", run.runId());
        metadata.put("status", status);
        metadata.put("error_code", errorCode);
        metadata.put(contextService.metadataKey(), run.conversationContext());
        insertMessage(
                run.conversationId(),
                "assistant",
                "本次回答未完成，请重试。",
                write(metadata),
                LocalDateTime.now());
    }
    /**
     * 查询当前用户的 AI 会话列表。
     *
     * @param authUserId 当前登录用户编号
     */
    public List<AiConversationView> conversations(Long authUserId) {
        return mapper.findConversations(profile(authUserId).id()).stream()
                .map(c -> new AiConversationView(c.id(), c.title(), c.createdAt(), c.updatedAt())).toList();
    }

    /**
     * 为当前用户创建 AI 会话。
     *
     * @param authUserId 当前登录用户编号
     * @param title 标题
     * @return 创建后的 AI 会话
     */
    @Transactional
    public AiConversationView createConversation(Long authUserId, String title) {
        AiConversation c = create(profile(authUserId).id(), normalizeTitle(title));
        return new AiConversationView(c.id(), c.title(), c.createdAt(), c.updatedAt());
    }

    /**
     * 查询指定会话或聊天室的消息记录。
     *
     * @param authUserId 当前登录用户编号
     * @param conversationId 会话编号
     */
    public List<AiMessageView> messages(Long authUserId, Long conversationId) {
        Long profileId = profile(authUserId).id();
        requireConversation(conversationId, profileId);
        List<AiMessage> messages = mapper.findRecentMessages(conversationId, 20);
        Collections.reverse(messages);
        return messages.stream().map(m -> new AiMessageView(m.id(), m.role(), m.content(),
                readMetadata(m.metadata()), m.createdAt())).toList();
    }

    /**
     * 解析请求中的会话编号，不存在时创建新会话。
     *
     * @param profileId 用户资料编号
     * @param request 请求参数
     */
    private AiConversation resolveConversation(Long profileId, AiChatRequest request) {
        if (request.conversationId() != null) {
            return requireConversation(request.conversationId(), profileId);
        }
        if (Boolean.TRUE.equals(request.newConversation())) {
            return create(profileId, "新对话");
        }
        AiConversation latest = mapper.findLatestConversation(profileId);
        return latest == null ? create(profileId, "新对话") : latest;
    }

    /**
     * 查询并校验 AI 会话归属关系。
     *
     * @param id 编号
     * @param profileId 用户资料编号
     */
    private AiConversation requireConversation(Long id, Long profileId) {
        AiConversation c = mapper.findConversation(id, profileId);
        if (c == null) {
            throw new BusinessException("AI_CONVERSATION_NOT_FOUND", "对话不存在", HttpStatus.NOT_FOUND);
        }
        return c;
    }

    /**
     * 创建业务数据并写入数据库。
     *
     * @param profileId 用户资料编号
     * @param title 标题
     * @return 创建后的 AI 会话
     */
    private AiConversation create(Long profileId, String title) {
        LocalDateTime now = LocalDateTime.now();
        AiMapper.MutableConversation row = new AiMapper.MutableConversation();
        row.userId = profileId;
        row.title = title;
        row.createdAt = now;
        row.updatedAt = now;
        mapper.insertConversation(row);
        return new AiConversation(row.id, row.userId, row.title, now, now);
    }

    /**
     * 新增消息记录。
     *
     * @param conversationId 会话编号
     * @param role 角色
     * @param content 内容
     * @param metadata 元数据
     * @param now 当前时间
     */
    private Long insertMessage(Long conversationId, String role, String content, String metadata, LocalDateTime now) {
        AiMapper.MutableMessage row = new AiMapper.MutableMessage();
        row.conversationId = conversationId;
        row.role = role;
        row.content = content;
        row.metadata = metadata;
        row.createdAt = now;
        mapper.insertMessage(row);
        return row.id;
    }

    /**
     * 根据认证用户编号查询对应用户资料。
     *
     * @param userId 用户编号
     */
    private UserProfile profile(Long userId) {
        UserProfile profile = users.findProfileByUserId(userId);
        if (profile == null) {
            throw new BusinessException("PROFILE_NOT_FOUND", "用户资料不存在", HttpStatus.BAD_REQUEST);
        }
        return profile;
    }

    /**
     * 规范化 AI 会话标题并限制最大长度。
     *
     * @param value 字段值
     */
    private String normalizeTitle(String value) {
        if (value == null || value.isBlank()) {
            return "新对话";
        }
        String normalizedTitle = value.trim();
        return normalizedTitle.substring(0, Math.min(200, normalizedTitle.length()));
    }

    /**
     * 将历史消息转换为模型上下文；房源编号只作为可信上下文提供给模型，
     * 不会污染用户在界面上看到的原始问题。
     */
    private AiModelGateway.ChatTurn toChatTurn(AiMessage message) {
        String content = message.content();
        if ("user".equalsIgnoreCase(message.role())) {
            Object houseId = readMetadata(message.metadata()).get("house_id");
            if (houseId instanceof Number number && number.longValue() > 0) {
                content = "用户当时选择的房源编号为 " + number.longValue() + "。用户问题：" + content;
            }
        }
        return new AiModelGateway.ChatTurn(message.role(), content);
    }

    /**
     * 保存用户当前选择的房源编号，供后续多轮对话恢复上下文。
     */
    private String userMetadata(Long houseId) {
        return houseId == null ? "{}" : write(Map.of("house_id", houseId));
    }

    /**
     * 创建并保存一条 AI 会话消息。
     *
     * @param value 字段值
     */
    private String write(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        }
        catch (Exception exception) {
            throw new IllegalStateException("无法序列化 AI 消息元数据", exception);
        }
    }

    /**
     * 从向量文档中读取房源编号元数据。
     *
     * @param value 字段值
     */
    private Map<String, Object> readMetadata(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(value, new TypeReference<>() {});
        }
        catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * 流式生成在准备阶段固化的上下文。
     */
    public record PreparedRun(
            String runId,
            Long authUserId,
            Long profileId,
            Long conversationId,
            String originalTitle,
            String query,
            Long selectedHouseId,
            String requestId,
            List<AiModelGateway.ChatTurn> history,
            AiConversationContext conversationContext,
            Long userMessageId) {
    }

    /**
     * 流式回答完成后的消息编号和兼容响应。
     */
    public record StreamCompletion(Long messageId, AiChatResponse response) {
    }
}
