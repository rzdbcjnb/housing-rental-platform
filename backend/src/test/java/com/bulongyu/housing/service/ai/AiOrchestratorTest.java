package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.vo.AiSourceView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiOrchestratorTest {
    private IntentService intentService;
    private HybridRagService houseRagService;
    private KnowledgeRagService knowledgeRagService;
    private AiModelGateway modelGateway;
    private RentalAgentService agentService;
    private AiMetrics metrics;
    private AiOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        intentService = mock(IntentService.class);
        houseRagService = mock(HybridRagService.class);
        knowledgeRagService = mock(KnowledgeRagService.class);
        modelGateway = mock(AiModelGateway.class);
        agentService = mock(RentalAgentService.class);
        metrics = mock(AiMetrics.class);
        orchestrator = new AiOrchestrator(
                intentService,
                houseRagService,
                knowledgeRagService,
                modelGateway,
                agentService,
                metrics);
    }

    @Test
    void routesHouseRecommendationToHybridRag() {
        String query = "帮我找大连两室房";
        List<AiModelGateway.ChatTurn> history = List.of();
        IntentResult intentResult = intent(IntentResult.Intent.HOUSE_RECOMMEND);
        HybridRagService.RagResult ragResult = new HybridRagService.RagResult(
                "推荐结果", "house_list", List.of(), List.of(),
                AiHouseSearchService.SearchStatus.MATCHED);
        when(intentService.detect(eq(query), any())).thenReturn(intentResult);
        when(houseRagService.recommend(query, intentResult, history)).thenReturn(ragResult);

        AiOrchestrator.Result result = orchestrator.answer(context(), query, history);

        assertThat(result.response()).isEqualTo("推荐结果");
        assertThat(result.type()).isEqualTo("house_list");
        assertThat(result.retrievalStatus()).isEqualTo("MATCHED");
        verify(houseRagService).recommend(query, intentResult, history);
        verifyNoInteractions(knowledgeRagService, modelGateway);
    }

    @Test
    void routesKnowledgeQuestionToKnowledgeRag() {
        String query = "押金一般什么时候退";
        List<AiModelGateway.ChatTurn> history = List.of();
        IntentResult intentResult = intent(IntentResult.Intent.KNOWLEDGE_QUERY);
        AiSourceView source = new AiSourceView("faq-1", "押金退还", "contract", 0.9);
        when(intentService.detect(eq(query), any())).thenReturn(intentResult);
        when(knowledgeRagService.answer(query, history))
                .thenReturn(new KnowledgeRagService.Answer("知识回答", List.of(source)));

        AiOrchestrator.Result result = orchestrator.answer(context(), query, history);

        assertThat(result.response()).isEqualTo("知识回答");
        assertThat(result.sources()).containsExactly(source);
        verify(knowledgeRagService).answer(query, history);
        verifyNoInteractions(houseRagService, modelGateway);
    }

    @Test
    void routesGeneralChatWithoutCallingRetrievalServices() {
        String query = "你好";
        List<AiModelGateway.ChatTurn> history = List.of();
        when(intentService.detect(eq(query), any())).thenReturn(intent(IntentResult.Intent.GENERAL_CHAT));
        when(modelGateway.available()).thenReturn(false);

        AiOrchestrator.Result result = orchestrator.answer(context(), query, history);

        assertThat(result.type()).isEqualTo("text");
        assertThat(result.response()).contains("查找房源");
        verifyNoInteractions(houseRagService, knowledgeRagService);
    }

    @Test
    void returnsClarificationWithoutCallingDownstreamServices() {
        String query = "帮我找房子";
        IntentResult intentResult = new IntentResult(
                IntentResult.Intent.HOUSE_RECOMMEND,
                List.of(),
                null,
                query,
                "请告诉我想租住的城市");
        when(intentService.detect(eq(query), any())).thenReturn(intentResult);

        AiOrchestrator.Result result = orchestrator.answer(context(), query, List.of());

        assertThat(result.type()).isEqualTo("clarification");
        assertThat(result.response()).isEqualTo("请告诉我想租住的城市");
        verifyNoInteractions(houseRagService, knowledgeRagService, modelGateway);
    }

    @Test
    void routesHouseComparisonToAgent() {
        String query = "比较第一套和第二套房源";
        List<AiModelGateway.ChatTurn> history = List.of();
        when(intentService.detect(eq(query), any())).thenReturn(intent(IntentResult.Intent.HOUSE_RECOMMEND));
        when(agentService.answer(any(AgentContext.class), eq(query), eq(history),
                any(AgentToolEventListener.class)))
                .thenReturn(new RentalAgentService.AgentResult("对比结果", 2, false, List.of(), List.of()));

        AiOrchestrator.Result result = orchestrator.answer(context(), query, history);

        assertThat(result.response()).isEqualTo("对比结果");
        assertThat(result.type()).isEqualTo("text");
        verify(agentService).answer(any(AgentContext.class), eq(query), eq(history),
                any(AgentToolEventListener.class));
        verifyNoInteractions(houseRagService, knowledgeRagService, modelGateway);
    }

    @Test
    void routesDirectSendFollowupToAgentInsteadOfGeneralChat() {
        String query = "直接发送";
        List<AiModelGateway.ChatTurn> history = List.of();
        when(intentService.detect(eq(query), any())).thenReturn(intent(IntentResult.Intent.GENERAL_CHAT));
        when(agentService.answer(any(AgentContext.class), eq(query), eq(history),
                any(AgentToolEventListener.class)))
                .thenReturn(new RentalAgentService.AgentResult(
                        "消息尚未发送", 0, false, List.of(), List.of()));

        AiOrchestrator.Result result = orchestrator.answer(context(), query, history);

        assertThat(result.response()).isEqualTo("消息尚未发送");
        verify(agentService).answer(any(AgentContext.class), eq(query), eq(history),
                any(AgentToolEventListener.class));
        verifyNoInteractions(houseRagService, knowledgeRagService, modelGateway);
    }

    @Test
    void routesLandlordFollowupWithResolvedConversationHouseToAgent() {
        String query = "帮我联系房东";
        List<AiModelGateway.ChatTurn> history = List.of(
                new AiModelGateway.ChatTurn("user", "房源151"),
                new AiModelGateway.ChatTurn("assistant", "房源详情"));
        String contextualQuery = "用户当前选择的房源编号为 151。用户问题：帮我联系房东";
        when(intentService.detect(eq(query), any())).thenReturn(new IntentResult(
                IntentResult.Intent.GENERAL_CHAT,
                List.of(),
                null,
                query,
                "抱歉，我无法直接联系房东"));
        when(agentService.answer(any(AgentContext.class), eq(contextualQuery), eq(history),
                any(AgentToolEventListener.class)))
                .thenReturn(new RentalAgentService.AgentResult("已生成消息预览", 1, false, List.of(), List.of()));

        AiOrchestrator.Result result = orchestrator.answer(
                context(), query, history, AgentToolEventListener.NO_OP, 151L);

        assertThat(result.response()).isEqualTo("已生成消息预览");
        verify(agentService).answer(any(AgentContext.class), eq(contextualQuery), eq(history),
                any(AgentToolEventListener.class));
        verifyNoInteractions(houseRagService, knowledgeRagService, modelGateway);
    }

    @Test
    void routesFavoriteToAgentBeforeModelClarification() {
        String query = "帮我收藏这个房源";
        List<AiModelGateway.ChatTurn> history = List.of();
        String contextualQuery = "用户当前选择的房源编号为 151。用户问题：帮我收藏这个房源";
        when(intentService.detect(eq(query), any())).thenReturn(new IntentResult(
                IntentResult.Intent.GENERAL_CHAT,
                List.of(),
                null,
                query,
                "暂不支持收藏操作"));
        when(agentService.answer(any(AgentContext.class), eq(contextualQuery), eq(history),
                any(AgentToolEventListener.class)))
                .thenReturn(new RentalAgentService.AgentResult(
                        "已生成收藏操作预览", 1, false, List.of(), List.of()));

        AiOrchestrator.Result result = orchestrator.answer(
                context(), query, history, AgentToolEventListener.NO_OP, 151L);

        assertThat(result.response()).isEqualTo("已生成收藏操作预览");
        verify(agentService).answer(any(AgentContext.class), eq(contextualQuery), eq(history),
                any(AgentToolEventListener.class));
        verifyNoInteractions(houseRagService, knowledgeRagService, modelGateway);
    }
    @Test
    void routesGenerateFollowupAfterContactPreviewQuestionToAgent() {
        String query = "生成";
        List<AiModelGateway.ChatTurn> history = List.of(
                new AiModelGateway.ChatTurn("assistant", "需要我为您生成联系房东的消息预览吗？"));
        String contextualQuery = "用户当前选择的房源编号为 1。用户问题：生成";
        when(intentService.detect(eq(query), any())).thenReturn(intent(IntentResult.Intent.GENERAL_CHAT));
        when(agentService.answer(any(AgentContext.class), eq(contextualQuery), eq(history),
                any(AgentToolEventListener.class)))
                .thenReturn(new RentalAgentService.AgentResult("已生成消息预览", 1, false, List.of(), List.of()));

        AiOrchestrator.Result result = orchestrator.answer(
                context(), query, history, AgentToolEventListener.NO_OP, 1L);

        assertThat(result.response()).isEqualTo("已生成消息预览");
        verify(agentService).answer(any(AgentContext.class), eq(contextualQuery), eq(history),
                any(AgentToolEventListener.class));
    }

    @Test
    void selectedHouseWaitsForAnExplicitQuestionAndRoutesOnlyThatHouseToAgent() {
        String query = "这套房采光怎么样";
        List<AiModelGateway.ChatTurn> history = List.of();
        when(intentService.detect(eq(query), any())).thenReturn(intent(IntentResult.Intent.GENERAL_CHAT));
        when(agentService.answer(any(AgentContext.class),
                eq("用户当前选择的房源编号为 153。用户问题：这套房采光怎么样"),
                eq(history), any(AgentToolEventListener.class)))
                .thenReturn(new RentalAgentService.AgentResult("详情结果", 1, false, List.of(), List.of()));

        AiOrchestrator.Result result = orchestrator.answer(
                context(), query, history, AgentToolEventListener.NO_OP, 153L);

        assertThat(result.response()).isEqualTo("详情结果");
        verify(agentService).answer(any(AgentContext.class),
                eq("用户当前选择的房源编号为 153。用户问题：这套房采光怎么样"),
                eq(history), any(AgentToolEventListener.class));
        verifyNoInteractions(houseRagService, knowledgeRagService, modelGateway);
    }

    private AgentContext context() {
        return new AgentContext(1L, 2L, "request-1");
    }
    private IntentResult intent(IntentResult.Intent intent) {
        return new IntentResult(intent, List.of(), null, "query", "");
    }
}
