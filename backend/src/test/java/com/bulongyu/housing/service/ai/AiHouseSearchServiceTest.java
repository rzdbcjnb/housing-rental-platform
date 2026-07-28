package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.HouseCandidate;
import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import com.bulongyu.housing.mapper.AiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bulongyu.housing.entity.SearchConstraint.Field.BATHROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.BEDROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.LIVING_ROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.PRICE;
import static com.bulongyu.housing.entity.SearchConstraint.Field.REGION;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.AROUND;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.CONTAINS;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.EQ;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.GTE;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.LTE;
import static com.bulongyu.housing.entity.SearchConstraint.Strength.HARD;
import static com.bulongyu.housing.entity.SearchConstraint.Strength.SOFT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiHouseSearchServiceTest {
    private SemanticRetriever semanticRetriever;
    private AiMapper aiMapper;
    private AiHouseSearchService service;

    @BeforeEach
    void setUp() {
        semanticRetriever = mock(SemanticRetriever.class);
        aiMapper = mock(AiMapper.class);
        service = new AiHouseSearchService(semanticRetriever, aiMapper);
    }

    @Test
    void degradesToStructuredMysqlWhenVectorStoreIsUnavailableAndHardConstraintsExist() {
        IntentResult intentResult = new IntentResult(
                IntentResult.Intent.HOUSE_RECOMMEND,
                List.of(
                        constraint(PRICE, LTE, new BigDecimal("2000"), HARD),
                        constraint(BEDROOMS, EQ, 2, HARD),
                        constraint(LIVING_ROOMS, GTE, 1, HARD),
                        constraint(BATHROOMS, GTE, 1, HARD),
                        constraint(REGION, CONTAINS, "大连", HARD)),
                null,
                "大连两室房",
                "");
        HouseCandidate house = house(1L, "正确房源", "1900");
        when(semanticRetriever.retrieveHouseIds("大连两室房", 50))
                .thenReturn(retrieval(SemanticRetriever.RetrievalStatus.UNAVAILABLE, List.of(), Map.of()));
        when(aiMapper.searchHouses(
                List.of(), null, new BigDecimal("2000"), 2, 2,
                1, 1, null, "大连"))
                .thenReturn(List.of(house));

        AiHouseSearchService.SearchResult result = service.search(intentResult, 5);

        assertThat(result.houses()).containsExactly(house);
        assertThat(result.status()).isEqualTo(AiHouseSearchService.SearchStatus.DEGRADED_STRUCTURED);
        assertThat(result.vectorActive()).isFalse();
        verify(aiMapper).searchHouses(
                List.of(), null, new BigDecimal("2000"), 2, 2,
                1, 1, null, "大连");
    }

    @Test
    void usesVectorCandidatesAndSortsBySoftPricePreferenceWhenVectorSearchSucceeds() {
        SearchConstraint softPrice = constraint(PRICE, AROUND, new BigDecimal("2000"), SOFT);
        IntentResult intentResult = new IntentResult(
                IntentResult.Intent.HOUSE_RECOMMEND,
                List.of(softPrice),
                null,
                "预算2000左右",
                "");
        HouseCandidate lowerMatch = house(1L, "接近预算", "1900");
        HouseCandidate distantMatch = house(2L, "远离预算", "1300");
        when(semanticRetriever.retrieveHouseIds("预算2000左右", 50))
                .thenReturn(retrieval(
                        SemanticRetriever.RetrievalStatus.SUCCESS_WITH_RESULTS,
                        List.of(1L, 2L),
                        Map.of(1L, 0.8, 2L, 0.8)));
        when(aiMapper.searchHouses(
                List.of(1L, 2L), null, null, null, null,
                null, null, null, null))
                .thenReturn(List.of(distantMatch, lowerMatch));

        AiHouseSearchService.SearchResult result = service.search(intentResult, 5);

        assertThat(result.houses()).containsExactly(lowerMatch, distantMatch);
        assertThat(result.status()).isEqualTo(AiHouseSearchService.SearchStatus.MATCHED);
    }

    @Test
    void returnsNoMatchWithoutQueryingMysqlWhenVectorSearchSucceedsEmpty() {
        IntentResult intentResult = semanticIntent("非常具体但没有命中的需求");
        when(semanticRetriever.retrieveHouseIds("非常具体但没有命中的需求", 50))
                .thenReturn(retrieval(
                        SemanticRetriever.RetrievalStatus.SUCCESS_EMPTY,
                        List.of(),
                        Map.of()));

        AiHouseSearchService.SearchResult result = service.search(intentResult, 5);

        assertThat(result.houses()).isEmpty();
        assertThat(result.status()).isEqualTo(AiHouseSearchService.SearchStatus.NO_MATCH);
        assertThat(result.vectorActive()).isTrue();
        verifyNoInteractions(aiMapper);
    }

    @Test
    void returnsUnavailableWithoutQueryingMysqlForPureSemanticRequest() {
        IntentResult intentResult = semanticIntent("想找安静且有海景的房子");
        when(semanticRetriever.retrieveHouseIds("想找安静且有海景的房子", 50))
                .thenReturn(retrieval(
                        SemanticRetriever.RetrievalStatus.UNAVAILABLE,
                        List.of(),
                        Map.of()));

        AiHouseSearchService.SearchResult result = service.search(intentResult, 5);

        assertThat(result.houses()).isEmpty();
        assertThat(result.status()).isEqualTo(AiHouseSearchService.SearchStatus.RETRIEVAL_UNAVAILABLE);
        assertThat(result.vectorActive()).isFalse();
        verifyNoInteractions(aiMapper);
    }

    @Test
    void reportsNoMatchWhenVectorCandidatesAreNoLongerPublic() {
        IntentResult intentResult = semanticIntent("近地铁的房子");
        when(semanticRetriever.retrieveHouseIds("近地铁的房子", 50))
                .thenReturn(retrieval(
                        SemanticRetriever.RetrievalStatus.SUCCESS_WITH_RESULTS,
                        List.of(99L),
                        Map.of(99L, 0.9)));
        when(aiMapper.searchHouses(
                List.of(99L), null, null, null, null,
                null, null, null, null))
                .thenReturn(List.of());

        AiHouseSearchService.SearchResult result = service.search(intentResult, 5);

        assertThat(result.status()).isEqualTo(AiHouseSearchService.SearchStatus.NO_MATCH);
        assertThat(result.houses()).isEmpty();
    }

    @Test
    void globallyRanksMoreThanFiftyStructuredCandidatesBeforeApplyingInternalLimit() {
        SearchConstraint hardPrice = constraint(PRICE, LTE, new BigDecimal("5000"), HARD);
        IntentResult intentResult = new IntentResult(
                IntentResult.Intent.HOUSE_RECOMMEND,
                List.of(hardPrice),
                null,
                "月租不超过5000元",
                "");
        List<HouseCandidate> candidates = new ArrayList<>();
        for (long id = 1; id <= 60; id++) {
            candidates.add(house(id, "候选房源" + id, "2000"));
        }
        Map<Long, Double> vectorScores = new LinkedHashMap<>();
        vectorScores.put(60L, 1.0);
        when(semanticRetriever.retrieveHouseIds("月租不超过5000元", 50))
                .thenReturn(retrieval(
                        SemanticRetriever.RetrievalStatus.SUCCESS_WITH_RESULTS,
                        List.of(60L),
                        vectorScores));
        when(aiMapper.searchHouses(
                List.of(), null, new BigDecimal("5000"), null, null,
                null, null, null, null))
                .thenReturn(candidates);

        AiHouseSearchService.SearchResult result = service.search(intentResult, 100);

        assertThat(result.houses()).hasSize(50);
        assertThat(result.houses().get(0).id()).isEqualTo(60L);
        assertThat(result.houses()).extracting(HouseCandidate::id)
                .contains(49L, 60L)
                .doesNotContain(50L, 59L);
        verify(aiMapper).searchHouses(
                List.of(), null, new BigDecimal("5000"), null, null,
                null, null, null, null);
    }

    private IntentResult semanticIntent(String query) {
        return new IntentResult(IntentResult.Intent.HOUSE_RECOMMEND, List.of(), null, query, "");
    }

    private SemanticRetriever.Retrieval retrieval(SemanticRetriever.RetrievalStatus status,
                                                  List<Long> ids,
                                                  Map<Long, Double> scores) {
        return new SemanticRetriever.Retrieval(status, ids, scores);
    }

    private SearchConstraint constraint(SearchConstraint.Field field,
                                        SearchConstraint.Operator operator,
                                        Object value,
                                        SearchConstraint.Strength strength) {
        return new SearchConstraint(field, operator, value, strength);
    }

    private HouseCandidate house(Long id, String title, String price) {
        return new HouseCandidate(
                id,
                title,
                "近地铁，采光好",
                new BigDecimal(price),
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