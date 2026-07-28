package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import com.bulongyu.housing.service.ChatService;
import com.bulongyu.housing.service.HouseService;
import com.bulongyu.housing.service.InteractionService;
import com.bulongyu.housing.vo.AiActionHouseView;
import com.bulongyu.housing.vo.AiActionResultView;
import com.bulongyu.housing.vo.AiPendingActionView;
import com.bulongyu.housing.vo.HouseDetailView;
import com.bulongyu.housing.vo.InteractionCreatedView;
import com.bulongyu.housing.websocket.ChatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * AI 写操作的准备与一次性确认服务，Agent 本身不能直接修改业务数据。
 */
@Service
public class AiActionService {
    public static final String FAVORITE_ACTION = "favorite";
    public static final String SEND_LANDLORD_MESSAGE_ACTION = "send_landlord_message";

    private static final Logger log = LoggerFactory.getLogger(AiActionService.class);
    private static final String ACTION_KEY_PREFIX = "ai:action:";
    private static final String INQUIRY_RATE_KEY_PREFIX = "ai:inquiry:rate:";
    private static final Duration ACTION_TTL = Duration.ofMinutes(5);
    private static final Duration INQUIRY_RATE_TTL = Duration.ofSeconds(30);
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final HouseService houseService;
    private final InteractionService interactionService;
    private final ChatService chatService;
    private final ChatWebSocketHandler webSocketHandler;
    private final UserMapper userMapper;

    /**
     * 初始化 AI 待确认操作服务。
     *
     * @param redis Redis 字符串操作组件
     * @param json JSON 序列化组件
     * @param houseService 房源业务服务
     * @param interactionService 收藏业务服务
     * @param chatService 聊天业务服务
     * @param webSocketHandler 聊天广播组件
     * @param userMapper 用户资料数据访问组件
     */
    public AiActionService(StringRedisTemplate redis,
                           ObjectMapper json,
                           HouseService houseService,
                           InteractionService interactionService,
                           ChatService chatService,
                           ChatWebSocketHandler webSocketHandler,
                           UserMapper userMapper) {
        this.redis = redis;
        this.json = json;
        this.houseService = houseService;
        this.interactionService = interactionService;
        this.chatService = chatService;
        this.webSocketHandler = webSocketHandler;
        this.userMapper = userMapper;
    }

    /**
     * 校验收藏目标并创建五分钟有效的一次性确认操作，不直接新增收藏。
     *
     * @param context 服务端可信 Agent 上下文
     * @param houseId 房源编号
     * @return 收藏操作预览
     */
    public AiPendingActionView prepareFavorite(AgentContext context, Long houseId) {
        HouseDetailView house = requirePublicHouse(context.userId(), houseId);
        if (interactionService.favoriteStatus(context.userId(), houseId).isFavorited()) {
            throw bad("FAVORITE_EXISTS", "已经收藏过该房源");
        }
        return store(context, FAVORITE_ACTION, house, null);
    }

    /**
     * 校验咨询文本和房源归属并创建一次性确认操作，不直接向房东发送消息。
     *
     * @param context 服务端可信 Agent 上下文
     * @param houseId 房源编号
     * @param content 用户确认后将原样发送的文本
     * @return 发送咨询操作预览
     */
    public AiPendingActionView prepareSendLandlordMessage(AgentContext context,
                                                           Long houseId,
                                                           String content) {
        HouseDetailView house = requirePublicHouse(context.userId(), houseId);
        UserProfile currentProfile = requireProfile(context.userId());
        if (currentProfile.id().equals(house.landlord())) {
            throw bad("SELF_HOUSE_INQUIRY", "不能联系自己发布的房源");
        }
        return store(context, SEND_LANDLORD_MESSAGE_ACTION, house, normalizeContent(content));
    }

    /**
     * 校验令牌归属并原子消费，随后调用现有业务服务执行用户已经确认的操作。
     *
     * @param userId 当前认证用户编号
     * @param conversationId 当前 AI 会话编号
     * @param token 一次性确认令牌
     * @return 操作执行结果
     */
    public AiActionResultView confirm(Long userId, Long conversationId, String token) {
        String normalizedToken = normalizeToken(token);
        String key = ACTION_KEY_PREFIX + normalizedToken;
        String value = redis.opsForValue().get(key);
        if (value == null) {
            throw new BusinessException(
                    "AI_ACTION_EXPIRED_OR_USED",
                    "确认操作已过期或已经使用",
                    HttpStatus.GONE);
        }
        PendingAction action = read(value);
        validateOwner(action, userId, conversationId);
        if (!hash(action).equals(action.argumentsHash())) {
            throw new BusinessException(
                    "AI_ACTION_TAMPERED",
                    "确认操作参数校验失败",
                    HttpStatus.CONFLICT);
        }
        Long consumed = redis.execute(CONSUME_SCRIPT, List.of(key), value);
        if (consumed == null || consumed != 1L) {
            throw new BusinessException(
                    "AI_ACTION_ALREADY_USED",
                    "确认操作已经使用",
                    HttpStatus.CONFLICT);
        }

        AiActionResultView result = execute(action);
        log.info("完成AI确认操作，参数：action={}，userId={}，conversationId={}，houseId={}",
                action.action(), userId, conversationId, action.houseId());
        return result;
    }

    /**
     * 将待确认操作保存到 Redis，并把令牌和最终预览返回给用户。
     */
    private AiPendingActionView store(AgentContext context,
                                      String actionType,
                                      HouseDetailView house,
                                      String content) {
        requireContext(context);
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = createdAt.plus(ACTION_TTL);
        PendingAction unsigned = new PendingAction(
                context.userId(),
                context.conversationId(),
                actionType,
                house.id(),
                content,
                "",
                "pending",
                createdAt,
                expiresAt);
        PendingAction pendingAction = unsigned.withArgumentsHash(hash(unsigned));
        String token = UUID.randomUUID().toString();
        Boolean stored = redis.opsForValue().setIfAbsent(
                ACTION_KEY_PREFIX + token,
                write(pendingAction),
                ACTION_TTL);
        if (!Boolean.TRUE.equals(stored)) {
            throw new BusinessException(
                    "AI_ACTION_STORE_FAILED",
                    "暂时无法创建确认操作，请稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        log.info("创建AI待确认操作，参数：action={}，userId={}，conversationId={}，houseId={}，ttlSeconds={}",
                actionType,
                context.userId(),
                context.conversationId(),
                house.id(),
                ACTION_TTL.toSeconds());
        return new AiPendingActionView(
                token,
                actionType,
                context.conversationId(),
                houseView(house),
                content,
                expiresAt);
    }

    /**
     * 根据已消费的白名单操作执行收藏或发送咨询。
     */
    private AiActionResultView execute(PendingAction action) {
        LocalDateTime executedAt = LocalDateTime.now();
        if (FAVORITE_ACTION.equals(action.action())) {
            InteractionCreatedView favorite = interactionService.addFavorite(
                    action.userId(), action.houseId());
            return new AiActionResultView(
                    action.action(), favorite.id(), null, null, null, executedAt);
        }
        if (SEND_LANDLORD_MESSAGE_ACTION.equals(action.action())) {
            reserveInquiryRate(action.userId(), action.houseId());
            ChatService.HouseInquiryResult inquiry = chatService.sendHouseInquiry(
                    action.userId(), action.houseId(), action.content());
            // 数据库事务已经提交后才广播；广播失败不会撤销已持久化的两条消息。
            webSocketHandler.broadcastHouseInquiry(inquiry);
            return new AiActionResultView(
                    action.action(),
                    null,
                    inquiry.roomId(),
                    inquiry.textMessage().id(),
                    inquiry.houseShareMessage().id(),
                    executedAt);
        }
        throw new BusinessException(
                "AI_ACTION_NOT_ALLOWED",
                "不支持的确认操作",
                HttpStatus.BAD_REQUEST);
    }

    /**
     * 限制同一用户短时间内重复咨询同一套房源。
     */
    private void reserveInquiryRate(Long userId, Long houseId) {
        String key = INQUIRY_RATE_KEY_PREFIX + userId + ":" + houseId;
        Boolean reserved = redis.opsForValue().setIfAbsent(key, "1", INQUIRY_RATE_TTL);
        if (!Boolean.TRUE.equals(reserved)) {
            throw new BusinessException(
                    "HOUSE_INQUIRY_TOO_FREQUENT",
                    "请勿频繁向同一房源发送咨询",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /**
     * 查询并校验普通用户可操作的公开房源。
     */
    private HouseDetailView requirePublicHouse(Long userId, Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw bad("HOUSE_ID_INVALID", "房源编号无效");
        }
        HouseDetailView house = houseService.detail(houseId, userId);
        if (!"approved".equals(house.status()) || !Boolean.TRUE.equals(house.isActive())) {
            throw new BusinessException("HOUSE_NOT_FOUND", "房源不存在", HttpStatus.NOT_FOUND);
        }
        return house;
    }

    /**
     * 校验当前用户资料存在。
     */
    private UserProfile requireProfile(Long userId) {
        UserProfile profile = userMapper.findProfileByUserId(userId);
        if (profile == null) {
            throw bad("PROFILE_NOT_FOUND", "用户资料不存在");
        }
        return profile;
    }

    /**
     * 校验待确认操作只能由创建它的用户在原会话中执行。
     */
    private void validateOwner(PendingAction action, Long userId, Long conversationId) {
        if (!action.userId().equals(userId) || !action.conversationId().equals(conversationId)) {
            throw new BusinessException(
                    "AI_ACTION_FORBIDDEN",
                    "无权执行该确认操作",
                    HttpStatus.FORBIDDEN);
        }
        if (!"pending".equals(action.status())) {
            throw new BusinessException(
                    "AI_ACTION_ALREADY_USED",
                    "确认操作已经使用",
                    HttpStatus.CONFLICT);
        }
    }

    /**
     * 校验服务端注入的用户和会话上下文。
     */
    private void requireContext(AgentContext context) {
        if (context == null || context.userId() == null || context.userId() <= 0
                || context.conversationId() == null || context.conversationId() <= 0) {
            throw new BusinessException(
                    "AI_ACTION_CONTEXT_INVALID",
                    "AI 确认操作上下文无效",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 规范化咨询文本，确认后将使用完全相同的内容发送。
     */
    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw bad("AI_ACTION_CONTENT_REQUIRED", "咨询内容不能为空");
        }
        String normalized = content.trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw bad("AI_ACTION_CONTENT_TOO_LONG", "咨询内容不能超过 300 个字符");
        }
        return normalized;
    }

    /**
     * 校验令牌格式，避免把任意字符串拼接为 Redis Key。
     */
    private String normalizeToken(String token) {
        try {
            return UUID.fromString(token).toString();
        }
        catch (RuntimeException exception) {
            throw bad("AI_ACTION_TOKEN_INVALID", "确认令牌无效");
        }
    }

    /**
     * 计算用户、会话和操作参数的稳定摘要，用于发现缓存内容异常修改。
     */
    private String hash(PendingAction action) {
        String canonical = action.userId() + "\n"
                + action.conversationId() + "\n"
                + action.action() + "\n"
                + action.houseId() + "\n"
                + (action.content() == null ? "" : action.content());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new IllegalStateException("无法计算 AI 操作参数摘要", exception);
        }
    }

    /**
     * 将房源详情转换为确认界面允许展示的公开字段。
     */
    private AiActionHouseView houseView(HouseDetailView house) {
        return new AiActionHouseView(
                house.id(),
                house.title(),
                house.price(),
                house.rooms(),
                house.image(),
                house.regionName());
    }

    /**
     * 将待确认操作序列化为 Redis 值。
     */
    private String write(PendingAction action) {
        try {
            return json.writeValueAsString(action);
        }
        catch (Exception exception) {
            throw new IllegalStateException("无法序列化 AI 待确认操作", exception);
        }
    }

    /**
     * 从 Redis 读取待确认操作。
     */
    private PendingAction read(String value) {
        try {
            return json.readValue(value, PendingAction.class);
        }
        catch (Exception exception) {
            throw new BusinessException(
                    "AI_ACTION_DATA_INVALID",
                    "确认操作数据无效",
                    HttpStatus.CONFLICT);
        }
    }

    /**
     * 创建参数错误类型业务异常。
     */
    private BusinessException bad(String code, String detail) {
        return new BusinessException(code, detail, HttpStatus.BAD_REQUEST);
    }

    /**
     * Redis 中保存的待确认操作数据。
     */
    private record PendingAction(
            Long userId,
            Long conversationId,
            String action,
            Long houseId,
            String content,
            String argumentsHash,
            String status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        private PendingAction withArgumentsHash(String value) {
            return new PendingAction(
                    userId,
                    conversationId,
                    action,
                    houseId,
                    content,
                    value,
                    status,
                    createdAt,
                    expiresAt);
        }
    }
}
