package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.vo.AiActionHouseView;
import com.bulongyu.housing.vo.AiPendingActionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RentalActionToolsTest {
    private AiActionService actionService;
    private RentalActionTools tools;
    private List<AiPendingActionView> pendingActions;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        actionService = mock(AiActionService.class);
        tools = new RentalActionTools(actionService);
        pendingActions = new CopyOnWriteArrayList<>();
        Map<String, Object> values = new HashMap<>();
        values.put(RentalReadTools.USER_ID_CONTEXT_KEY, 1L);
        values.put(RentalReadTools.CONVERSATION_ID_CONTEXT_KEY, 2L);
        values.put(RentalReadTools.REQUEST_ID_CONTEXT_KEY, "request-1");
        values.put(RentalReadTools.TOOL_CALL_COUNTER_CONTEXT_KEY, new AtomicInteger());
        values.put(RentalReadTools.TOOL_CALL_LIMIT_CONTEXT_KEY, 6);
        values.put(RentalActionTools.PENDING_ACTIONS_CONTEXT_KEY, pendingActions);
        toolContext = new ToolContext(values);
    }

    @Test
    void preparesLandlordMessageWithoutExecutingIt() {
        AiPendingActionView expected = pendingAction("send_landlord_message");
        when(actionService.prepareSendLandlordMessage(
                any(AgentContext.class), eq(10L), eq("您好，可以发更多细节图吗？")))
                .thenReturn(expected);

        AiPendingActionView result = tools.prepareSendLandlordMessage(
                10L,
                "您好，可以发更多细节图吗？",
                toolContext);

        assertThat(result).isEqualTo(expected);
        assertThat(pendingActions).containsExactly(expected);
        verify(actionService).prepareSendLandlordMessage(
                new AgentContext(1L, 2L, "request-1"),
                10L,
                "您好，可以发更多细节图吗？");
    }

    @Test
    void enforcesSharedAgentToolBudget() {
        when(actionService.prepareFavorite(any(AgentContext.class), eq(10L)))
                .thenReturn(pendingAction("favorite"));
        Map<String, Object> values = new HashMap<>(toolContext.getContext());
        values.put(RentalReadTools.TOOL_CALL_LIMIT_CONTEXT_KEY, 1);
        ToolContext limitedContext = new ToolContext(values);

        tools.prepareFavorite(10L, limitedContext);

        assertThatThrownBy(() -> tools.prepareFavorite(10L, limitedContext))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("AI_TOOL_CALL_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsContextWithoutConversationIdentity() {
        ToolContext invalidContext = new ToolContext(Map.of(
                RentalReadTools.USER_ID_CONTEXT_KEY, 1L));

        assertThatThrownBy(() -> tools.prepareFavorite(10L, invalidContext))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上下文无效");
    }

    private AiPendingActionView pendingAction(String action) {
        return new AiPendingActionView(
                "token",
                action,
                2L,
                new AiActionHouseView(
                        10L,
                        "测试房源",
                        new BigDecimal("2000"),
                        "2室1厅",
                        "",
                        "大连"),
                null,
                LocalDateTime.now().plusMinutes(5));
    }
}
