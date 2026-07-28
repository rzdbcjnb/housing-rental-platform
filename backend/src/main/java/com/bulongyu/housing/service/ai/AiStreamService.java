package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.dto.AiChatRequest;
import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.entity.AgentToolTrace;
import com.bulongyu.housing.vo.AiPendingActionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 客服 SSE 流式编排服务。
 */
@Service
public class AiStreamService {
    private static final Logger log = LoggerFactory.getLogger(AiStreamService.class);
    private static final int DELTA_CHUNK_SIZE = 24;

    private final AiConversationService conversationService;
    private final AiOrchestrator orchestrator;
    private final ExecutorService executor;
    private final ScheduledExecutorService heartbeatScheduler;
    private final Duration streamTimeout;
    private final AiMetrics metrics;
    private final Semaphore streamPermits;
    private final ConcurrentMap<String, Long> startTimes = new ConcurrentHashMap<>();

    /**
     * 初始化 AI SSE 流式服务。
     *
     * @param conversationService AI 会话持久化服务
     * @param orchestrator AI 请求编排服务
     * @param executor SSE 编排有界线程池
     * @param heartbeatScheduler SSE 心跳调度器
     * @param streamTimeout 单次 SSE 连接超时
     * @param maxConcurrentStreams 最大并发 SSE 连接数
     * @param metrics AI 指标记录器
     */
    public AiStreamService(
            AiConversationService conversationService,
            AiOrchestrator orchestrator,
            @Qualifier("aiStreamExecutor") ExecutorService executor,
            @Qualifier("aiHeartbeatScheduler") ScheduledExecutorService heartbeatScheduler,
            @Value("${app.ai.stream.timeout:60s}") Duration streamTimeout,
            @Value("${app.ai.stream.max-concurrent:50}") int maxConcurrentStreams,
            AiMetrics metrics) {
        this.conversationService = conversationService;
        this.orchestrator = orchestrator;
        this.executor = executor;
        this.heartbeatScheduler = heartbeatScheduler;
        this.streamTimeout = streamTimeout;
        this.metrics = metrics;
        this.streamPermits = new Semaphore(maxConcurrentStreams, true);
    }

    /**
     * 准备会话后异步执行 RAG 或 Agent，并按标准事件协议返回结果。
     *
     * @param userId 当前认证用户编号
     * @param request AI 对话请求
     * @param requestId 请求追踪编号
     * @return SSE 连接
     */
    public SseEmitter stream(Long userId, AiChatRequest request, String requestId) {
        if (!streamPermits.tryAcquire()) {
            throw new BusinessException(
                    "AI_STREAM_CAPACITY_EXCEEDED",
                    "AI 客服当前请求较多，请稍后重试",
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        }
        AiConversationService.PreparedRun run;
        try {
            run = conversationService.prepareStream(userId, request, requestId);
        }
        catch (RuntimeException exception) {
            streamPermits.release();
            throw exception;
        }
        startTimes.put(run.runId(), System.nanoTime());
        SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        AtomicBoolean terminal = new AtomicBoolean(false);
        AtomicReference<Future<?>> taskReference = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> heartbeatReference = new AtomicReference<>();

        try {
            send(emitter, "conversation", Map.of(
                    "conversation_id", run.conversationId(),
                    "run_id", run.runId()));
            send(emitter, "heartbeat", Map.of());
        }
        catch (RuntimeException exception) {
            recordTerminal(run, "cancelled");
            conversationService.failStream(run, "cancelled", "AI_STREAM_CLIENT_DISCONNECTED");
            throw exception;
        }

        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> heartbeat(emitter, run, terminal, taskReference, heartbeatReference),
                15,
                15,
                TimeUnit.SECONDS);
        heartbeatReference.set(heartbeat);

        emitter.onTimeout(() -> terminate(
                emitter,
                run,
                terminal,
                taskReference,
                heartbeatReference,
                "cancelled",
                "AI_STREAM_TIMEOUT"));
        emitter.onError(exception -> terminate(
                emitter,
                run,
                terminal,
                taskReference,
                heartbeatReference,
                "failed",
                "AI_STREAM_CONNECTION_ERROR"));
        emitter.onCompletion(() -> terminate(
                emitter,
                run,
                terminal,
                taskReference,
                heartbeatReference,
                "cancelled",
                "AI_STREAM_CLIENT_DISCONNECTED"));

        try {
            Future<?> task = executor.submit(() -> generate(
                    emitter,
                    run,
                    terminal,
                    taskReference,
                    heartbeatReference));
            taskReference.set(task);
        }
        catch (RejectedExecutionException exception) {
            trySend(emitter, "error", Map.of(
                    "code", "AI_STREAM_CAPACITY_EXCEEDED",
                    "message", "AI 客服当前请求较多，请稍后重试",
                    "request_id", run.requestId()));
            terminate(
                    emitter,
                    run,
                    terminal,
                    taskReference,
                    heartbeatReference,
                    "failed",
                    "AI_STREAM_CAPACITY_EXCEEDED");
        }
        return emitter;
    }

    /**
     * 在异步线程中执行编排、增量输出和完成阶段持久化。
     */
    private void generate(SseEmitter emitter,
                          AiConversationService.PreparedRun run,
                          AtomicBoolean terminal,
                          AtomicReference<Future<?>> taskReference,
                          AtomicReference<ScheduledFuture<?>> heartbeatReference) {
        try {
            send(emitter, "status", Map.of(
                    "stage", "routing",
                    "message", "正在分析需求"));
            AgentToolEventListener listener = listener(emitter);
            AgentContext context = new AgentContext(
                    run.authUserId(), run.conversationId(), run.requestId());
            AiOrchestrator.Result result = orchestrator.answer(
                    context, run.query(), run.history(), listener, run.selectedHouseId());
            send(emitter, "status", Map.of(
                    "stage", "generating",
                    "message", "正在生成回答"));
            recordFirstDelta(run);
            sendDeltas(emitter, result.response());
            for (AiPendingActionView action : result.pendingActions()) {
                send(emitter, "pending_action", action);
            }
            AiConversationService.StreamCompletion completion =
                    conversationService.completeStream(run, result);
            if (terminal.compareAndSet(false, true)) {
                cancelHeartbeat(heartbeatReference);
                recordTerminal(run, "completed");
                Map<String, Object> completedEvent = new LinkedHashMap<>();
                completedEvent.put("message_id", completion.messageId());
                completedEvent.put("conversation_id", run.conversationId());
                completedEvent.put("type", result.type());
                completedEvent.put("houses", result.houses());
                completedEvent.put("sources", result.sources());
                completedEvent.put("pending_actions", result.pendingActions());
                if (result.retrievalStatus() != null) {
                    completedEvent.put("retrieval_status", result.retrievalStatus());
                }
                send(emitter, "completed", completedEvent);
                emitter.complete();
            }
        }
        catch (StreamDisconnectedException exception) {
            terminate(
                    emitter,
                    run,
                    terminal,
                    taskReference,
                    heartbeatReference,
                    "cancelled",
                    "AI_STREAM_CLIENT_DISCONNECTED");
        }
        catch (RuntimeException exception) {
            String code = exception instanceof BusinessException businessException
                    ? businessException.getCode()
                    : "AI_STREAM_FAILED";
            trySend(emitter, "error", Map.of(
                    "code", code,
                    "message", safeMessage(exception),
                    "request_id", run.requestId()));
            terminate(
                    emitter,
                    run,
                    terminal,
                    taskReference,
                    heartbeatReference,
                    "failed",
                    code);
        }
    }

    /**
     * 创建把工具开始和结果转换成 SSE 事件的监听器。
     */
    private AgentToolEventListener listener(SseEmitter emitter) {
        return new AgentToolEventListener() {
            @Override
            public void onStart(String toolName) {
                send(emitter, "tool_start", Map.of("tool", toolName));
            }

            @Override
            public void onResult(AgentToolTrace trace) {
                send(emitter, "tool_result", Map.of(
                        "tool", trace.name(),
                        "status", trace.status(),
                        "duration_ms", trace.durationMs(),
                        "result_count", trace.resultCount()));
            }
        };
    }

    /**
     * 将完整回答切分为稳定大小的增量事件。
     */
    private void sendDeltas(SseEmitter emitter, String response) {
        for (int offset = 0; offset < response.length(); offset += DELTA_CHUNK_SIZE) {
            int end = Math.min(response.length(), offset + DELTA_CHUNK_SIZE);
            send(emitter, "delta", Map.of("content", response.substring(offset, end)));
        }
    }

    /**
     * 定时发送心跳；发送失败时按客户端断开处理。
     */
    private void heartbeat(SseEmitter emitter,
                           AiConversationService.PreparedRun run,
                           AtomicBoolean terminal,
                           AtomicReference<Future<?>> taskReference,
                           AtomicReference<ScheduledFuture<?>> heartbeatReference) {
        if (terminal.get()) {
            cancelHeartbeat(heartbeatReference);
            return;
        }
        try {
            send(emitter, "heartbeat", Map.of());
        }
        catch (StreamDisconnectedException exception) {
            terminate(
                    emitter,
                    run,
                    terminal,
                    taskReference,
                    heartbeatReference,
                    "cancelled",
                    "AI_STREAM_CLIENT_DISCONNECTED");
        }
    }

    /**
     * 原子结束流式请求并记录失败或取消状态。
     */
    private void terminate(SseEmitter emitter,
                           AiConversationService.PreparedRun run,
                           AtomicBoolean terminal,
                           AtomicReference<Future<?>> taskReference,
                           AtomicReference<ScheduledFuture<?>> heartbeatReference,
                           String status,
                           String errorCode) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        Future<?> task = taskReference.get();
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        cancelHeartbeat(heartbeatReference);
        try {
            conversationService.failStream(run, status, errorCode);
        }
        catch (RuntimeException exception) {
            log.warn("记录AI流式失败状态异常，参数：runId={}，exceptionType={}",
                    run.runId(), exception.getClass().getSimpleName());
        }
        recordTerminal(run, status);
        emitter.complete();
        log.info("结束AI流式请求，参数：runId={}，status={}，errorCode={}",
                run.runId(), status, errorCode);
    }

    /**
     * 取消当前连接的心跳任务。
     */
    private void cancelHeartbeat(AtomicReference<ScheduledFuture<?>> heartbeatReference) {
        ScheduledFuture<?> heartbeat = heartbeatReference.get();
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
    }

    /**
     * 向 SSE 连接发送具名事件，失败时转换为内部断开信号。
     */
    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            }
        }
        catch (IOException | IllegalStateException exception) {
            throw new StreamDisconnectedException(exception);
        }
    }

    /**
     * 尽力发送错误事件，连接已关闭时不覆盖原始异常。
     */
    private void trySend(SseEmitter emitter, String eventName, Object data) {
        try {
            send(emitter, eventName, data);
        }
        catch (StreamDisconnectedException ignored) {
        }
    }

    /**
     * 记录首次回答增量耗时。
     */
    private void recordFirstDelta(AiConversationService.PreparedRun run) {
        Long startedAt = startTimes.get(run.runId());
        if (startedAt != null) {
            metrics.recordFirstDelta(elapsedMillis(startedAt));
        }
    }

    /**
     * 原子释放 SSE 并发许可并记录最终状态和完整耗时。
     */
    private void recordTerminal(AiConversationService.PreparedRun run, String status) {
        Long startedAt = startTimes.remove(run.runId());
        if (startedAt != null) {
            streamPermits.release();
            metrics.recordStream(status, elapsedMillis(startedAt));
        }
    }

    /**
     * 计算纳秒起点到当前时刻的毫秒数。
     */
    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
    /**
     * 只向客户端返回业务异常消息，其他异常使用统一提示。
     */
    private String safeMessage(RuntimeException exception) {
        if (exception instanceof BusinessException) {
            return exception.getMessage();
        }
        return "AI 客服处理失败，请稍后重试";
    }

    /**
     * SSE 客户端断开内部信号。
     */
    private static final class StreamDisconnectedException extends RuntimeException {
        private StreamDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
