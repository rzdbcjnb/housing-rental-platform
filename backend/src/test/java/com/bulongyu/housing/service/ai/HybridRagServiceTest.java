package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.HouseCandidate;
import com.bulongyu.housing.entity.IntentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HybridRagServiceTest {
    private AiHouseSearchService houseSearchService;
    private AiModelGateway model;
    private HybridRagService service;
    private IntentResult intent;

    @BeforeEach
    void setUp() {
        houseSearchService = mock(AiHouseSearchService.class);
        model = mock(AiModelGateway.class);
        service = new HybridRagService(houseSearchService, model);
        intent = new IntentResult(
                IntentResult.Intent.HOUSE_RECOMMEND,
                List.of(),
                null,
                "安静且近地铁",
                "");
    }

    @Test
    void explainsSuccessfulNoMatchWithoutCallingModel() {
        when(houseSearchService.search(intent, 5)).thenReturn(result(
                AiHouseSearchService.SearchStatus.NO_MATCH,
                List.of()));

        HybridRagService.RagResult result = service.recommend(
                "安静且近地铁",
                intent,
                List.of());

        assertThat(result.retrievalStatus()).isEqualTo(AiHouseSearchService.SearchStatus.NO_MATCH);
        assertThat(result.type()).isEqualTo("text");
        assertThat(result.houses()).isEmpty();
        assertThat(result.response()).contains("没有找到匹配");
        verifyNoInteractions(model);
    }

    @Test
    void explainsUnavailablePureSemanticRetrievalWithoutCallingModel() {
        when(houseSearchService.search(intent, 5)).thenReturn(result(
                AiHouseSearchService.SearchStatus.RETRIEVAL_UNAVAILABLE,
                List.of()));

        HybridRagService.RagResult result = service.recommend(
                "安静且近地铁",
                intent,
                List.of());

        assertThat(result.retrievalStatus())
                .isEqualTo(AiHouseSearchService.SearchStatus.RETRIEVAL_UNAVAILABLE);
        assertThat(result.response()).contains("语义检索服务暂时不可用");
        assertThat(result.houses()).isEmpty();
        verifyNoInteractions(model);
    }

    @Test
    void labelsStructuredFallbackAndDoesNotClaimSemanticMatch() {
        HouseCandidate house = house(1L);
        when(houseSearchService.search(intent, 5)).thenReturn(result(
                AiHouseSearchService.SearchStatus.DEGRADED_STRUCTURED,
                List.of(house)));

        HybridRagService.RagResult result = service.recommend(
                "安静且近地铁",
                intent,
                List.of());

        assertThat(result.retrievalStatus())
                .isEqualTo(AiHouseSearchService.SearchStatus.DEGRADED_STRUCTURED);
        assertThat(result.type()).isEqualTo("house_list");
        assertThat(result.houses()).singleElement().satisfies(view ->
                assertThat(view.id()).isEqualTo(1L));
        assertThat(result.response())
                .contains("仅满足当前可执行的价格、地区或户型条件")
                .doesNotContain("满足语义偏好");
        verifyNoInteractions(model);
    }

    private AiHouseSearchService.SearchResult result(AiHouseSearchService.SearchStatus status,
                                                     List<HouseCandidate> houses) {
        return new AiHouseSearchService.SearchResult(houses, status);
    }

    private HouseCandidate house(Long id) {
        return new HouseCandidate(
                id,
                "大连两室房",
                "近地铁，采光好",
                new BigDecimal("1900"),
                75,
                "2室1厅1卫1厨",
                2,
                1,
                1,
                1,
                "高新街道",
                "甘井子区",
                "大连",
                "");
    }
}
