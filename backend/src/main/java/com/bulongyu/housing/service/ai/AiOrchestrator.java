package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.entity.AgentToolTrace;
import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.vo.AiHouseView;
import com.bulongyu.housing.vo.AiPendingActionView;
import com.bulongyu.housing.vo.AiSourceView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * AI 客服请求编排服务。
 */
@Service
public class AiOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AiOrchestrator.class);
    private static final Pattern COMPLEX_TASK_PATTERN = Pattern.compile(
            "比较|对比|哪套|哪一套|区别|更适合|详情|详细信息|第一套|第二套|这套|那套|收藏|联系.{0,20}房东|发消息|发给房东|打招呼|更多细节图|更多图片|直接发送|确认发送|就这样发|帮我发"
    );
    private static final Pattern ACTION_TASK_PATTERN = Pattern.compile(
            "收藏|联系.{0,20}房东|发消息|发给房东|打招呼|更多细节图|更多图片|直接发送|确认发送|就这样发|帮我发"
    );

    private final IntentService intentService;
    private final HybridRagService houseRagService;
    private final KnowledgeRagService knowledgeRagService;
    private final AiModelGateway modelGateway;
    private final RentalAgentService agentService;
    private final AiMetrics metrics;

    /**
     * 初始化 AI 请求编排服务。
     *
     * @param intentService 意图识别服务
     * @param houseRagService 房源混合检索服务
     * @param knowledgeRagService 租房知识检索服务
     * @param modelGateway AI 模型网关
     * @param agentService 租房客服单 Agent 服务
     * @param metrics AI 指标记录器
     */
    public AiOrchestrator(IntentService intentService,
                          HybridRagService houseRagService,
                          KnowledgeRagService knowledgeRagService,
                          AiModelGateway modelGateway,
                          RentalAgentService agentService,
                          AiMetrics metrics) {
        this.intentService = intentService;
        this.houseRagService = houseRagService;
        this.knowledgeRagService = knowledgeRagService;
        this.modelGateway = modelGateway;
        this.agentService = agentService;
        this.metrics = metrics;
    }

    /**
     * 识别用户意图并将请求路由到对应的稳定处理链路。
     *
     * @param context 服务端可信 Agent 上下文
     * @param query 规范化后的用户问题
     * @param history 按时间正序排列的最近对话
     * @return AI 处理结果
     */
    public Result answer(AgentContext context,
                         String query,
                         List<AiModelGateway.ChatTurn> history) {
        return answer(context, query, history, AgentToolEventListener.NO_OP, null);
    }

    /**
     * 识别用户意图并把 Agent 工具事件转发给流式调用方。
     *
     * @param context 服务端可信 Agent 上下文
     * @param query 规范化后的用户问题
     * @param history 最近对话历史
     * @param eventListener 工具执行事件监听器
     * @return AI 处理结果
     */
    public Result answer(AgentContext context,
                         String query,
                         List<AiModelGateway.ChatTurn> history,
                         AgentToolEventListener eventListener) {
        return answer(context, query, history, eventListener, null);
    }

    /**
     * 识别用户意图，并在用户主动选择房源时把可信房源编号交给 Agent。
     *
     * @param context 服务端可信 Agent 上下文
     * @param query 用户实际输入的问题
     * @param history 最近对话历史
     * @param eventListener 工具执行事件监听器
     * @param selectedHouseId 用户当前选择的房源编号
     * @return AI 处理结果
     */
    public Result answer(AgentContext context,
                         String query,
                         List<AiModelGateway.ChatTurn> history,
                         AgentToolEventListener eventListener,
                         Long selectedHouseId) {
        IntentResult intentResult = intentService.detect(query, history);
        log.info("完成AI请求路由，参数：intent={}，queryLength={}，historyCount={}",
                intentResult.intent(), query.length(), history.size());
        // 明确写操作由服务端待确认工具处理，不能被意图模型生成的澄清文本提前截断。
        if (requiresActionAgent(query, history)) {
            return fromAgent(context, query, history, eventListener, selectedHouseId);
        }
        if (!intentResult.clarification().isBlank()) {
            metrics.recordRoute("clarification");
            return new Result(intentResult.clarification(), "clarification", List.of(), List.of(), List.of(), List.of());
        }
        if (requiresAgent(query, intentResult, selectedHouseId, history)) {
            return fromAgent(context, query, history, eventListener, selectedHouseId);
        }
        return switch (intentResult.intent()) {
            case HOUSE_RECOMMEND -> {
                metrics.recordRoute("house_rag");
                yield fromHouseRag(houseRagService.recommend(query, intentResult, history));
            }
            case KNOWLEDGE_QUERY -> {
                metrics.recordRoute("knowledge_rag");
                yield fromKnowledge(knowledgeRagService.answer(query, history));
            }
            case HOUSE_DETAIL, HOUSE_SIMILAR, GENERAL_CHAT -> {
                metrics.recordRoute("general");
                yield general(query, history);
            }
        };
    }

    /**
     * 将需要工具调用的请求交给 Agent，并注入服务端确认的当前房源编号。
     */
    private Result fromAgent(AgentContext context,
                             String query,
                             List<AiModelGateway.ChatTurn> history,
                             AgentToolEventListener eventListener,
                             Long selectedHouseId) {
        metrics.recordRoute("agent");
        String agentQuery = selectedHouseId == null
                ? query
                : "用户当前选择的房源编号为 " + selectedHouseId + "。用户问题：" + query;
        RentalAgentService.AgentResult agentResult = agentService.answer(
                context, agentQuery, history, eventListener);
        return new Result(agentResult.response(), "text", List.of(), List.of(),
                agentResult.pendingActions(), agentResult.toolTraces());
    }

    /**
     * 判断当前请求是否是必须进入待确认工具链的显式操作。
     */
    private boolean requiresActionAgent(String query, List<AiModelGateway.ChatTurn> history) {
        return ACTION_TASK_PATTERN.matcher(query).find()
                || confirmsContactPreview(query, history);
    }
    /**
     * 仅将需要连续查询、详情补充或确定性比较的复杂任务交给 Agent。
     */
    private boolean requiresAgent(String query,
                                  IntentResult intentResult,
                                  Long selectedHouseId,
                                  List<AiModelGateway.ChatTurn> history) {
        if (selectedHouseId != null && intentResult.intent() != IntentResult.Intent.GENERAL_CHAT) {
            return true;
        }
        if (intentResult.intent() == IntentResult.Intent.HOUSE_DETAIL
                || intentResult.intent() == IntentResult.Intent.HOUSE_SIMILAR) {
            return true;
        }
        return COMPLEX_TASK_PATTERN.matcher(query).find()
                || confirmsContactPreview(query, history);
    }

    /**
     * 只有上一条助手消息明确询问是否生成联系房东预览时，才把简短的“生成”识别为操作续接。
     */
    private boolean confirmsContactPreview(String query, List<AiModelGateway.ChatTurn> history) {
        if (query == null || !query.trim().matches("生成|生成吧|帮我生成|确认生成")) {
            return false;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            AiModelGateway.ChatTurn turn = history.get(index);
            if ("assistant".equalsIgnoreCase(turn.role())) {
                return turn.content() != null
                        && turn.content().matches(".*(?:生成|创建).*(?:联系房东|消息预览).*");
            }
        }
        return false;
    }
    /**
     * 将房源 RAG 结果转换为编排层统一结果。
     */
    private Result fromHouseRag(HybridRagService.RagResult result) {
        return new Result(result.response(), result.type(), result.houses(), result.sources(), List.of(), List.of(),
                result.retrievalStatus() == null ? null : result.retrievalStatus().name());
    }

    /**
     * 将知识 RAG 结果转换为编排层统一结果。
     */
    private Result fromKnowledge(KnowledgeRagService.Answer answer) {
        return new Result(answer.response(), "text", List.of(), answer.sources(), List.of(), List.of());
    }

    /**
     * 处理不需要检索业务数据的普通客服对话。
     */
    private Result general(String query, List<AiModelGateway.ChatTurn> history) {
        if (!modelGateway.available()) {
            return new Result("你好，我可以帮你查找房源、解释租房问题或比较房源。",
                    "text", List.of(), List.of(), List.of(), List.of());
        }
        String response = modelGateway.complete(
                """
                你是租房平台客服，只回答当前用户明确提出的问题，历史消息仅用于理解上下文。
                当前用户只是问候时只需正常问候，不得根据历史主动发起收藏、联系房东或房源比较。
                你没有执行写操作的权限，不得声称消息已发送、房源已收藏或其他操作已经完成。
                涉及写操作时只能说明需要通过页面上的确认操作执行。
                """,
                history,
                query);
        return new Result(response, "text", List.of(), List.of(), List.of(), List.of());
    }

    /**
     * 编排层统一返回结果。
     */
    public record Result(String response,
                         String type,
                         List<AiHouseView> houses,
                         List<AiSourceView> sources,
                         List<AiPendingActionView> pendingActions,
                         List<AgentToolTrace> toolTraces,
                         String retrievalStatus) {
        public Result(String response,
                      String type,
                      List<AiHouseView> houses,
                      List<AiSourceView> sources,
                      List<AiPendingActionView> pendingActions,
                      List<AgentToolTrace> toolTraces) {
            this(response, type, houses, sources, pendingActions, toolTraces, null);
        }
    }
}
