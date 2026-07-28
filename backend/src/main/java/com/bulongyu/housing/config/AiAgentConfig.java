package com.bulongyu.housing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI Agent 有界执行线程池配置。
 */
@Configuration
public class AiAgentConfig {
    /**
     * 创建 Agent 专用线程池，限制并发和排队数量，避免慢模型调用耗尽 Web 请求线程。
     *
     * @return Agent 执行线程池
     */
    @Bean(name = "aiAgentExecutor", destroyMethod = "shutdown")
    public ExecutorService aiAgentExecutor() {
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("ai-agent-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                2,
                8,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(50),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建 SSE 编排专用线程池，避免与内部 Agent 模型调用线程互相等待。
     *
     * @return SSE 编排线程池
     */
    @Bean(name = "aiStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService aiStreamExecutor() {
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("ai-stream-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                4,
                16,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }
    /**
     * 创建 SSE 心跳调度器，线程仅负责轻量心跳发送和断开检测。
     *
     * @return SSE 心跳调度器
     */
    @Bean(name = "aiHeartbeatScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService aiHeartbeatScheduler() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("ai-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newScheduledThreadPool(2, threadFactory);
    }
}
