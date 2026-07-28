package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.dto.HouseSearchToolRequest;
import com.bulongyu.housing.entity.HouseCandidate;
import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import com.bulongyu.housing.service.HouseService;
import com.bulongyu.housing.vo.HouseDetailView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RentalReadToolsTest {
    private AiHouseSearchService houseSearchService;
    private HouseService houseService;
    private KnowledgeRagService knowledgeRagService;
    private RentalReadTools tools;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        houseSearchService = mock(AiHouseSearchService.class);
        houseService = mock(HouseService.class);
        knowledgeRagService = mock(KnowledgeRagService.class);
        tools = new RentalReadTools(houseSearchService, houseService, knowledgeRagService);
        toolContext = new ToolContext(Map.of(RentalReadTools.USER_ID_CONTEXT_KEY, 9L));
    }

    @Test
    void translatesWhitelistedSearchArgumentsIntoHardAndSoftConstraints() {
        HouseSearchToolRequest request = new HouseSearchToolRequest(
                "大连两室房，预算2000左右，可以更少",
                "大连",
                null,
                new BigDecimal("2000"),
                new BigDecimal("2000"),
                2,
                null,
                1,
                1,
                null,
                5);
        HouseCandidate house = candidate(1L, "正确房源", "1900");
        when(houseSearchService.search(any(IntentResult.class), eq(5)))
                .thenReturn(new AiHouseSearchService.SearchResult(
                        List.of(house),
                        AiHouseSearchService.SearchStatus.DEGRADED_STRUCTURED));

        var result = tools.searchHouses(request, toolContext);

        assertThat(result.status()).isEqualTo("DEGRADED_STRUCTURED");
        assertThat(result.houses()).singleElement().satisfies(houseView -> {
            assertThat(houseView.id()).isEqualTo(1L);
            assertThat(houseView.price()).isEqualByComparingTo("1900");
        });
        ArgumentCaptor<IntentResult> intentCaptor = ArgumentCaptor.forClass(IntentResult.class);
        verify(houseSearchService).search(intentCaptor.capture(), eq(5));
        IntentResult intentResult = intentCaptor.getValue();
        assertThat(intentResult.constraints()).anyMatch(constraint -> matchesConstraint(
                constraint,
                SearchConstraint.Field.PRICE,
                SearchConstraint.Operator.LTE,
                SearchConstraint.Strength.HARD));
        assertThat(intentResult.constraints()).anyMatch(constraint -> matchesConstraint(
                constraint,
                SearchConstraint.Field.PRICE,
                SearchConstraint.Operator.AROUND,
                SearchConstraint.Strength.SOFT));
        assertThat(intentResult.constraints()).anyMatch(constraint -> matchesConstraint(
                constraint,
                SearchConstraint.Field.BEDROOMS,
                SearchConstraint.Operator.EQ,
                SearchConstraint.Strength.HARD));
    }

    @Test
    void returnsSafeHouseDetailWithoutLandlordOrAddressFields() {
        String longDescription = "采光好".repeat(500);
        when(houseService.detail(1L, 9L)).thenReturn(detail(1L, "公开房源", longDescription));

        RentalReadTools.HouseToolDetail result = tools.getHouseDetail(1L, toolContext);

        assertThat(result.description()).hasSize(1000);
        assertThat(Arrays.stream(RentalReadTools.HouseToolDetail.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("addressDetail", "landlord", "landlordInfo");
        verify(houseService).detail(1L, 9L);
    }

    @Test
    void comparesTwoHousesInRequestedOrder() {
        when(houseService.detail(2L, 9L)).thenReturn(detail(2L, "第二套", "描述二"));
        when(houseService.detail(1L, 9L)).thenReturn(detail(1L, "第一套", "描述一"));

        List<RentalReadTools.HouseComparisonItem> results = tools.compareHouses(
                List.of(2L, 1L),
                toolContext);

        assertThat(results).extracting(RentalReadTools.HouseComparisonItem::id)
                .containsExactly(2L, 1L);
    }

    @Test
    void rejectsDuplicateComparisonIds() {
        assertThatThrownBy(() -> tools.compareHouses(List.of(1L, 1L), toolContext))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复");
    }

    @Test
    void rejectsToolCallsWithoutServerUserContext() {
        HouseSearchToolRequest request = new HouseSearchToolRequest(
                "大连两室房", null, null, null, null,
                null, null, null, null, null, 5);

        assertThatThrownBy(() -> tools.searchHouses(request, new ToolContext(Map.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上下文无效");
    }

    @Test
    void delegatesKnowledgeSearchWithoutInvokingModelGeneration() {
        KnowledgeRagService.SearchResult expected = new KnowledgeRagService.SearchResult(
                "",
                List.of(new KnowledgeRagService.KnowledgeSnippet(
                        "faq-1", "押金退还", "contract", "合同约定优先", 0.9)),
                List.of());
        when(knowledgeRagService.search("押金什么时候退")).thenReturn(expected);

        KnowledgeRagService.SearchResult result = tools.searchKnowledge(
                "  押金什么时候退  ",
                toolContext);

        assertThat(result).isEqualTo(expected);
        verify(knowledgeRagService).search("押金什么时候退");
    }

    @Test
    void rejectsToolCallsAfterAgentBudgetIsExhausted() {
        KnowledgeRagService.SearchResult expected = new KnowledgeRagService.SearchResult(
                "", List.of(), List.of());
        when(knowledgeRagService.search("押金问题")).thenReturn(expected);
        ToolContext limitedContext = new ToolContext(Map.of(
                RentalReadTools.USER_ID_CONTEXT_KEY, 9L,
                RentalReadTools.TOOL_CALL_COUNTER_CONTEXT_KEY, new AtomicInteger(),
                RentalReadTools.TOOL_CALL_LIMIT_CONTEXT_KEY, 1));

        tools.searchKnowledge("押金问题", limitedContext);

        assertThatThrownBy(() -> tools.searchKnowledge("押金问题", limitedContext))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("AI_TOOL_CALL_LIMIT_EXCEEDED");
    }
    @Test
    void toolSurfaceDoesNotExposeUserIdentityOrArbitrarySqlParameters() {
        List<String> parameterNames = Arrays.stream(RentalReadTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .map(parameter -> parameter.getName().toLowerCase())
                .toList();

        assertThat(parameterNames)
                .noneMatch(name -> name.contains("userid")
                        || name.contains("sql")
                        || name.contains("table")
                        || name.contains("column"));
    }
    private boolean matchesConstraint(SearchConstraint constraint,
                                      SearchConstraint.Field field,
                                      SearchConstraint.Operator operator,
                                      SearchConstraint.Strength strength) {
        return constraint.field() == field
                && constraint.operator() == operator
                && constraint.strength() == strength;
    }

    private HouseCandidate candidate(Long id, String title, String price) {
        return new HouseCandidate(
                id, title, "公开描述", new BigDecimal(price), 75,
                "2室1厅1卫1厨", 2, 1, 1, 1,
                "高新街道", "甘井子区", "大连", "");
    }

    private HouseDetailView detail(Long id, String title, String description) {
        LocalDateTime now = LocalDateTime.now();
        return new HouseDetailView(
                id, title, description, new BigDecimal("1900"), 75,
                "2室1厅1卫1厨", 2, 1, 1, 1,
                "whole", "整租", 3L, "大连-甘井子区-高新街道",
                "敏感详细地址", "", 7L, null,
                "approved", "已通过", true, now, now);
    }
}
