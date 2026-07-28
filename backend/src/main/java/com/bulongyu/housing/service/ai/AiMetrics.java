package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.AgentToolTrace;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI 路由、工具和流式请求的 Micrometer 指标记录器。
 */
@Component
public class AiMetrics {
    private final MeterRegistry registry;

    /**
     * 初始化 AI 指标记录器。
     *
     * @param registry Micrometer 指标注册表
     */
    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次 AI 路由选择。
     *
     * @param route 稳定的路由名称
     */
    public void recordRoute(String route) {
        registry.counter("housing.ai.route.total", "route", route).increment();
    }

    /**
     * 记录工具成功率、耗时和结果数量。
     *
     * @param trace 安全工具执行摘要
     */
    public void recordTool(AgentToolTrace trace) {
        registry.counter(
                "housing.ai.tool.total",
                "tool", trace.name(),
                "status", trace.status()).increment();
        Timer.builder("housing.ai.tool.duration")
                .tag("tool", trace.name())
                .tag("status", trace.status())
                .register(registry)
                .record(Duration.ofMillis(trace.durationMs()));
        registry.summary(
                "housing.ai.tool.result.count",
                "tool", trace.name()).record(trace.resultCount());
    }

    /**
     * 记录 SSE 首次回答增量耗时。
     *
     * @param durationMs 首次增量耗时毫秒数
     */
    public void recordFirstDelta(long durationMs) {
        registry.timer("housing.ai.stream.first.delta")
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * 记录 SSE 完整执行耗时和最终状态。
     *
     * @param status completed、failed 或 cancelled
     * @param durationMs 完整耗时毫秒数
     */
    public void recordStream(String status, long durationMs) {
        registry.counter("housing.ai.stream.total", "status", status).increment();
        Timer.builder("housing.ai.stream.duration")
                .tag("status", status)
                .register(registry)
                .record(Duration.ofMillis(durationMs));
    }
}
