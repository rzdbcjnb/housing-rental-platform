package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.dto.AiChatRequest;
import com.bulongyu.housing.entity.AiConversationContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiStreamServiceTest {
    @Test
    void rejectsRequestsBeyondConfiguredConcurrentLimit() {
        AiConversationService conversationService = mock(AiConversationService.class);
        AiOrchestrator orchestrator = mock(AiOrchestrator.class);
        ExecutorService executor = mock(ExecutorService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        AiMetrics metrics = mock(AiMetrics.class);
        Future<?> task = mock(Future.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        AiConversationService.PreparedRun run = new AiConversationService.PreparedRun(
                "run-1", 1L, 2L, 3L, "新对话", "比较房源", null, "request-1",
                List.of(), AiConversationContext.empty(), 4L);
        when(conversationService.prepareStream(eq(1L), any(AiChatRequest.class), eq("request-1")))
                .thenReturn(run);
        doReturn(task).when(executor).submit(any(Runnable.class));
        doReturn(heartbeat).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class), anyLong(), anyLong(), any());
        AiStreamService service = new AiStreamService(
                conversationService,
                orchestrator,
                executor,
                scheduler,
                Duration.ofSeconds(60),
                1,
                metrics);
        AiChatRequest request = new AiChatRequest("比较房源", null, true);

        service.stream(1L, request, "request-1");

        assertThatThrownBy(() -> service.stream(1L, request, "request-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("AI_STREAM_CAPACITY_EXCEEDED");
    }
}
