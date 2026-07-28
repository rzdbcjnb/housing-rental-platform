package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.vo.AiActionHouseView;
import com.bulongyu.housing.vo.AiPendingActionView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RentalAgentServiceTest {
    private AiAgentGateway gateway;
    private RentalReadTools tools;
    private RentalActionTools actionTools;
    private AiMetrics metrics;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        gateway = mock(AiAgentGateway.class);
        tools = mock(RentalReadTools.class);
        actionTools = mock(RentalActionTools.class);
        metrics = mock(AiMetrics.class);
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void returnsSafeFallbackWhenModelIsUnavailable() {
        when(gateway.available()).thenReturn(false);
        RentalAgentService service = service(Duration.ofSeconds(1));

        RentalAgentService.AgentResult result = service.answer(context(), "比较两套房源", List.of());

        assertThat(result.fallback()).isTrue();
        assertThat(result.toolCallCount()).isZero();
        assertThat(result.response()).contains("可靠房源数据");
    }

    @Test
    void returnsSafeFallbackWhenModelDoesNotCallTool() {
        when(gateway.available()).thenReturn(true);
        when(gateway.complete(any(), any(), any(), any(), any()))
                .thenReturn("未经工具验证的回答");
        RentalAgentService service = service(Duration.ofSeconds(1));

        RentalAgentService.AgentResult result = service.answer(context(), "比较两套房源", List.of());

        assertThat(result.fallback()).isTrue();
        assertThat(result.response()).doesNotContain("未经工具验证");
    }

    @Test
    void acceptsMultiStepResultAfterToolsWereCalled() {
        when(gateway.available()).thenReturn(true);
        when(gateway.complete(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Map<String, Object> toolContext = invocation.getArgument(4);
                    AtomicInteger counter = (AtomicInteger) toolContext.get(
                            RentalReadTools.TOOL_CALL_COUNTER_CONTEXT_KEY);
                    counter.incrementAndGet();
                    counter.incrementAndGet();
                    return "房源 1 比房源 2 面积更大";
                });
        RentalAgentService service = service(Duration.ofSeconds(1));

        RentalAgentService.AgentResult result = service.answer(context(), "搜索后比较房源", List.of());

        assertThat(result.fallback()).isFalse();
        assertThat(result.toolCallCount()).isEqualTo(2);
        assertThat(result.response()).contains("房源 1");
    }

    @Test
    void createsFavoritePreviewFromTrustedConversationHouse() {
        when(actionTools.prepareFavorite(eq(151L), any(ToolContext.class)))
                .thenReturn(pendingFavoriteAction());
        RentalAgentService service = service(Duration.ofSeconds(1));

        RentalAgentService.AgentResult result = service.answer(
                context(),
                "用户当前选择的房源编号为 151。用户问题：帮我收藏这个房源",
                List.of());

        assertThat(result.pendingActions()).hasSize(1);
        assertThat(result.pendingActions().get(0).action()).isEqualTo(AiActionService.FAVORITE_ACTION);
        assertThat(result.response()).contains("房源尚未收藏").contains("确认");
    }

    @Test
    void loadsExplicitHouseDetailFromDatabaseToolInsteadOfCandidateHistory() {
        when(tools.getHouseDetail(eq(151L), any(ToolContext.class)))
                .thenReturn(houseDetail());
        RentalAgentService service = service(Duration.ofSeconds(1));

        RentalAgentService.AgentResult result = service.answer(
                context(),
                "用户当前选择的房源编号为 151。用户问题：151房源",
                List.of(new AiModelGateway.ChatTurn("assistant", "候选房源为1、2、5、6、7")));

        assertThat(result.response())
                .contains("房源151")
                .contains("大连高新区科技住宅")
                .contains("2800元/月")
                .doesNotContain("候选房源中没有");
    }

    @Test
    void createsContactPreviewFromTrustedConversationHouseWhenModelSkipsTool() {
        when(gateway.available()).thenReturn(true);
        when(gateway.complete(any(), any(), any(), any(), any()))
                .thenReturn("您可以联系房东");
        when(actionTools.prepareSendLandlordMessage(
                eq(151L), any(String.class), any(ToolContext.class)))
                .thenReturn(pendingSendAction());
        RentalAgentService service = service(Duration.ofSeconds(1));

        RentalAgentService.AgentResult result = service.answer(
                context(),
                "用户当前选择的房源编号为 151。用户问题：帮我联系房东",
                List.of());

        assertThat(result.pendingActions()).hasSize(1);
        assertThat(result.pendingActions().get(0).house().id()).isEqualTo(10L);
        assertThat(result.response()).contains("消息尚未发送").contains("确认");
    }

    @Test
    void createsPendingActionWhenUserConfirmsContactPreviewGeneration() {
        when(gateway.available()).thenReturn(true);
        when(gateway.complete(any(), any(), any(), any(), any()))
                .thenReturn("以下是消息预览");
        when(actionTools.prepareSendLandlordMessage(
                eq(1L), any(String.class), any(ToolContext.class)))
                .thenReturn(pendingSendAction());
        RentalAgentService service = service(Duration.ofSeconds(1));
        List<AiModelGateway.ChatTurn> history = List.of(
                new AiModelGateway.ChatTurn("assistant", "需要我为您生成联系房东的消息预览吗？"));

        RentalAgentService.AgentResult result = service.answer(
                context(),
                "用户当前选择的房源编号为 1。用户问题：生成",
                history);

        assertThat(result.pendingActions()).hasSize(1);
        assertThat(result.response()).contains("消息尚未发送").contains("确认");
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesFalseSentClaimWithDeterministicPendingPreview() {
        when(gateway.available()).thenReturn(true);
        when(gateway.complete(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Map<String, Object> toolContext = invocation.getArgument(4);
                    AtomicInteger counter = (AtomicInteger) toolContext.get(
                            RentalReadTools.TOOL_CALL_COUNTER_CONTEXT_KEY);
                    counter.incrementAndGet();
                    List<AiPendingActionView> actions = (List<AiPendingActionView>) toolContext.get(
                            RentalActionTools.PENDING_ACTIONS_CONTEXT_KEY);
                    actions.add(pendingSendAction());
                    return "已为您发送给房东";
                });
        RentalAgentService service = service(Duration.ofSeconds(1));

        RentalAgentService.AgentResult result = service.answer(
                context(), "联系房东", List.of());

        assertThat(result.pendingActions()).hasSize(1);
        assertThat(result.response()).contains("消息尚未发送").contains("确认");
        assertThat(result.response()).doesNotContain("已为您发送");
    }

    @Test
    void blocksFalseCompletionClaimWhenOnlyReadToolWasCalled() {
        when(gateway.available()).thenReturn(true);
        when(gateway.complete(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Map<String, Object> toolContext = invocation.getArgument(4);
                    ((AtomicInteger) toolContext.get(RentalReadTools.TOOL_CALL_COUNTER_CONTEXT_KEY))
                            .incrementAndGet();
                    return "好的，消息已发送";
                });
        RentalAgentService service = service(Duration.ofSeconds(1));

        RentalAgentService.AgentResult result = service.answer(
                context(), "直接发送", List.of());

        assertThat(result.response()).contains("消息尚未发送");
        assertThat(result.response()).doesNotContain("消息已发送");
    }

    @Test
    void stopsAgentWhenExecutionTimesOut() {
        when(gateway.available()).thenReturn(true);
        when(gateway.complete(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(200);
                    return "迟到的回答";
                });
        RentalAgentService service = service(Duration.ofMillis(20));

        assertThatThrownBy(() -> service.answer(context(), "比较两套房源", List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("AI_AGENT_TIMEOUT");
    }

    private AiPendingActionView pendingFavoriteAction() {
        return new AiPendingActionView(
                "favorite-token",
                AiActionService.FAVORITE_ACTION,
                2L,
                new AiActionHouseView(151L, "大连高新区科技住宅", new BigDecimal("2800"),
                        "1室1厅1卫", "", "大连"),
                null,
                LocalDateTime.now().plusMinutes(5));
    }

    private RentalReadTools.HouseToolDetail houseDetail() {
        return new RentalReadTools.HouseToolDetail(
                151L,
                "大连高新区科技住宅",
                "月付无押金，水电全免。",
                new BigDecimal("2800"),
                60,
                "1室1厅1卫",
                1,
                1,
                1,
                0,
                "大连高新区",
                "");
    }

    private AiPendingActionView pendingSendAction() {
        return new AiPendingActionView(
                "token",
                AiActionService.SEND_LANDLORD_MESSAGE_ACTION,
                2L,
                new AiActionHouseView(10L, "测试房源", new BigDecimal("2000"),
                        "2室1厅", "", "大连"),
                "您好，我对这套房子感兴趣。",
                LocalDateTime.now().plusMinutes(5));
    }

    private RentalAgentService service(Duration timeout) {
        return new RentalAgentService(gateway, tools, actionTools, executor, timeout, 6, metrics);
    }

    private AgentContext context() {
        return new AgentContext(1L, 2L, "request-1");
    }
}
