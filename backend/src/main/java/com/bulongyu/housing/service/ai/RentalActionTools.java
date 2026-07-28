package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.entity.AgentToolTrace;
import com.bulongyu.housing.vo.AiPendingActionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Agent 可调用的待确认操作工具，只生成预览和令牌，不写入业务数据。
 */
@Component
public class RentalActionTools {
    public static final String PENDING_ACTIONS_CONTEXT_KEY = "pendingActions";

    private static final Logger log = LoggerFactory.getLogger(RentalActionTools.class);

    private final AiActionService actionService;

    /**
     * 初始化 Agent 待确认操作工具。
     *
     * @param actionService AI 待确认操作服务
     */
    public RentalActionTools(AiActionService actionService) {
        this.actionService = actionService;
    }

    /**
     * 为收藏房源创建一次性确认令牌，不直接新增收藏。
     *
     * @param houseId 房源编号
     * @param toolContext 服务端可信工具上下文
     * @return 收藏操作预览
     */
    @Tool(name = "prepareFavorite", description = "准备收藏一套房源并返回确认预览。此工具不会直接收藏，必须等待用户确认。")
    public AiPendingActionView prepareFavorite(
            @ToolParam(description = "要收藏的房源编号") Long houseId,
            ToolContext toolContext) {
        return execute(
                toolContext,
                "prepareFavorite",
                houseId,
                context -> actionService.prepareFavorite(context, houseId));
    }

    /**
     * 为向房东发送“文本 + 房源卡片”创建确认令牌，不直接发送消息。
     *
     * @param houseId 房源编号
     * @param content 确认后将原样发送的文本
     * @param toolContext 服务端可信工具上下文
     * @return 发送咨询操作预览
     */
    @Tool(name = "prepareSendLandlordMessage", description = "准备向房东发送咨询文字和房源卡片并返回确认预览。不会直接发送，必须等待用户确认。")
    public AiPendingActionView prepareSendLandlordMessage(
            @ToolParam(description = "要咨询的房源编号") Long houseId,
            @ToolParam(description = "用户确认后将原样发送的咨询文字，最多300字") String content,
            ToolContext toolContext) {
        return execute(
                toolContext,
                "prepareSendLandlordMessage",
                houseId,
                context -> actionService.prepareSendLandlordMessage(context, houseId, content));
    }

    /**
     * 在统一边界内执行待确认工具并输出安全的 SSE 工具摘要。
     */
    private AiPendingActionView execute(ToolContext toolContext,
                                        String toolName,
                                        Long houseId,
                                        Function<AgentContext, AiPendingActionView> action) {
        AgentContext context = context(toolContext);
        int invocation = reserveToolCall(toolContext);
        AgentToolEventListener listener = listener(toolContext);
        long startedAt = System.nanoTime();
        listener.onStart(toolName);
        try {
            AiPendingActionView result = action.apply(context);
            long durationMs = elapsedMillis(startedAt);
            recordPendingAction(toolContext, result);
            listener.onResult(new AgentToolTrace(toolName, "success", durationMs, 1));
            log.info("完成AI待确认工具调用，参数：tool={}，userId={}，houseId={}，invocation={}，durationMs={}",
                    toolName, context.userId(), houseId, invocation, durationMs);
            return result;
        }
        catch (RuntimeException exception) {
            long durationMs = elapsedMillis(startedAt);
            listener.onResult(new AgentToolTrace(toolName, "failed", durationMs, 0));
            log.warn("AI待确认工具调用失败，参数：tool={}，userId={}，houseId={}，invocation={}，durationMs={}，exceptionType={}",
                    toolName,
                    context.userId(),
                    houseId,
                    invocation,
                    durationMs,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    /**
     * 读取本轮 Agent 注入的工具事件监听器。
     */
    private AgentToolEventListener listener(ToolContext toolContext) {
        Object value = toolContext.getContext().get(AgentToolEventListener.CONTEXT_KEY);
        return value instanceof AgentToolEventListener listener
                ? listener
                : AgentToolEventListener.NO_OP;
    }

    /**
     * 计算工具执行耗时并转换为毫秒。
     */
    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
    /**
     * 从服务端注入的 ToolContext 还原可信 Agent 上下文。
     */
    private AgentContext context(ToolContext toolContext) {
        if (toolContext == null) {
            throw invalidContext();
        }
        Object userId = toolContext.getContext().get(RentalReadTools.USER_ID_CONTEXT_KEY);
        Object conversationId = toolContext.getContext().get(RentalReadTools.CONVERSATION_ID_CONTEXT_KEY);
        Object requestId = toolContext.getContext().get(RentalReadTools.REQUEST_ID_CONTEXT_KEY);
        if (!(userId instanceof Number userNumber) || userNumber.longValue() <= 0
                || !(conversationId instanceof Number conversationNumber)
                || conversationNumber.longValue() <= 0) {
            throw invalidContext();
        }
        return new AgentContext(
                userNumber.longValue(),
                conversationNumber.longValue(),
                requestId == null ? "" : requestId.toString());
    }

    /**
     * 原子占用一次 Agent 工具调用预算。
     */
    private int reserveToolCall(ToolContext toolContext) {
        Object counterValue = toolContext.getContext().get(
                RentalReadTools.TOOL_CALL_COUNTER_CONTEXT_KEY);
        if (!(counterValue instanceof AtomicInteger counter)) {
            throw invalidContext();
        }
        Object limitValue = toolContext.getContext().get(
                RentalReadTools.TOOL_CALL_LIMIT_CONTEXT_KEY);
        int limit = limitValue instanceof Number number ? number.intValue() : 6;
        int invocation = counter.incrementAndGet();
        if (limit <= 0 || invocation > limit) {
            throw new BusinessException(
                    "AI_TOOL_CALL_LIMIT_EXCEEDED",
                    "本轮 AI 工具调用次数已达上限",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        return invocation;
    }
    /**
     * 将工具产生的待确认操作放入本轮执行摘要，供同步响应和 SSE 事件返回。
     */
    @SuppressWarnings("unchecked")
    private void recordPendingAction(ToolContext toolContext, AiPendingActionView action) {
        Object value = toolContext.getContext().get(PENDING_ACTIONS_CONTEXT_KEY);
        if (value instanceof List<?> actions) {
            ((List<AiPendingActionView>) actions).add(action);
        }
    }

    /**
     * 创建工具上下文无效异常。
     */
    private BusinessException invalidContext() {
        return new BusinessException(
                "AI_TOOL_CONTEXT_INVALID",
                "AI 工具上下文无效",
                HttpStatus.UNAUTHORIZED);
    }
}
