package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.AiConversationContext;
import com.bulongyu.housing.entity.AiMessage;
import com.bulongyu.housing.vo.AiHouseView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiConversationContextServiceTest {
    private ObjectMapper json;
    private AiConversationContextService service;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        service = new AiConversationContextService(json, new IntentService(mock(AiModelGateway.class)));
    }

    @Test
    void restoresHouseIdFromOldConversationUserMessage() {
        List<AiMessage> history = List.of(
                message(1L, "user", "房源151", "{}"),
                message(2L, "assistant", "房源详情", "{}"));

        AiConversationContext context = service.resolve(history, null, "帮我联系房东");

        assertThat(context.currentHouseId()).isEqualTo(151L);
    }

    @Test
    void currentExplicitHouseIdOverridesSelectedCardAndSnapshot() throws Exception {
        AiConversationContext snapshot = new AiConversationContext(153L, List.of(), List.of(), "HOUSE_DETAIL");

        AiConversationContext context = service.resolve(
                List.of(message(1L, "assistant", "详情", metadata(snapshot))),
                152L,
                "看看151号房源");

        assertThat(context.currentHouseId()).isEqualTo(151L);
    }

    @Test
    void resolvesChineseOrdinalHouseReferenceFromPersistedCandidates() throws Exception {
        AiConversationContext snapshot = new AiConversationContext(
                null, List.of(1L, 2L, 3L), List.of(), "HOUSE_RECOMMEND");

        AiConversationContext context = service.resolve(
                List.of(message(1L, "assistant", "推荐结果", metadata(snapshot))),
                null,
                "帮我联系第一个房源的房东");

        assertThat(context.currentHouseId()).isEqualTo(1L);
    }

    @Test
    void resolvesOrdinalReferenceFromPersistedCandidateSnapshot() throws Exception {
        AiConversationContext snapshot = new AiConversationContext(
                null, List.of(151L, 153L), List.of(), "HOUSE_RECOMMEND");

        AiConversationContext context = service.resolve(
                List.of(message(1L, "assistant", "推荐结果", metadata(snapshot))),
                null,
                "第二套怎么样");

        assertThat(context.currentHouseId()).isEqualTo(153L);
    }

    @Test
    void newSearchClearsPreviouslyDiscussedHouse() throws Exception {
        AiConversationContext snapshot = new AiConversationContext(151L, List.of(), List.of(), "HOUSE_DETAIL");

        AiConversationContext context = service.resolve(
                List.of(message(1L, "assistant", "详情", metadata(snapshot))),
                null,
                "帮我找2000元以内的房子");

        assertThat(context.currentHouseId()).isNull();
        assertThat(context.lastIntent()).isEqualTo("HOUSE_RECOMMEND");
    }

    @Test
    void storesRecommendationCandidatesAndClearsPreviousCurrentHouse() {
        AiConversationContext current = new AiConversationContext(151L, List.of(), List.of(), "HOUSE_RECOMMEND");
        AiOrchestrator.Result result = new AiOrchestrator.Result(
                "推荐", "house_list", List.of(house(152L), house(153L)), List.of(), List.of(), List.of());

        AiConversationContext completed = service.afterResponse(current, result);

        assertThat(completed.currentHouseId()).isNull();
        assertThat(completed.candidateHouseIds()).containsExactly(152L, 153L);
    }

    @Test
    void clearsStaleCandidatesAfterEmptyHouseSearchOutcome() {
        AiConversationContext current = new AiConversationContext(
                151L, List.of(152L, 153L), List.of(), "HOUSE_RECOMMEND");
        AiOrchestrator.Result result = new AiOrchestrator.Result(
                "没有匹配房源",
                "text",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "NO_MATCH");

        AiConversationContext completed = service.afterResponse(current, result);

        assertThat(completed.currentHouseId()).isNull();
        assertThat(completed.candidateHouseIds()).isEmpty();
    }

    private String metadata(AiConversationContext context) throws Exception {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put(service.metadataKey(), context);
        return json.writeValueAsString(metadata);
    }

    private AiMessage message(Long id, String role, String content, String metadata) {
        return new AiMessage(id, 1L, role, content, metadata, LocalDateTime.now());
    }

    private AiHouseView house(Long id) {
        return new AiHouseView(id, "房源" + id, BigDecimal.valueOf(2800), 60,
                "1室1厅1卫", 1, 1, 1, 1, "", "大连");
    }
}