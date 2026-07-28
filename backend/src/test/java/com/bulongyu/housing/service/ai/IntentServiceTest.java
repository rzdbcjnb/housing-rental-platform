package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentServiceTest {
    private final IntentService service = new IntentService(new AiModelGateway() {
        @Override public boolean available() { return false; }
        @Override public String complete(String systemPrompt, List<ChatTurn> history, String userPrompt) {
            throw new UnsupportedOperationException();
        }
    });

    @Test
    void extractsDirectionalBudgetAndHardRoomRequirements() {
        IntentResult result = service.detect("我想要在大连租一间房子，价格在2000左右，可以更少。要两室必须要有至少一卫还要有客厅。");

        assertThat(result.intent()).isEqualTo(IntentResult.Intent.HOUSE_RECOMMEND);
        assertThat(result.constraints()).anySatisfy(c -> assertConstraint(c,
                SearchConstraint.Field.PRICE, SearchConstraint.Operator.LTE, new BigDecimal("2000"), SearchConstraint.Strength.HARD));
        assertThat(result.constraints()).anySatisfy(c -> assertConstraint(c,
                SearchConstraint.Field.BEDROOMS, SearchConstraint.Operator.EQ, 2, SearchConstraint.Strength.HARD));
        assertThat(result.constraints()).anySatisfy(c -> assertConstraint(c,
                SearchConstraint.Field.BATHROOMS, SearchConstraint.Operator.GTE, 1, SearchConstraint.Strength.HARD));
        assertThat(result.constraints()).anySatisfy(c -> assertConstraint(c,
                SearchConstraint.Field.LIVING_ROOMS, SearchConstraint.Operator.GTE, 1, SearchConstraint.Strength.HARD));
        assertThat(result.constraints()).anySatisfy(c -> assertConstraint(c,
                SearchConstraint.Field.REGION, SearchConstraint.Operator.CONTAINS, "大连", SearchConstraint.Strength.HARD));
    }

    @Test
    void recognizesExplicitHouseNumberAsDetailIntent() {
        IntentResult result = service.detect("房源151");

        assertThat(result.intent()).isEqualTo(IntentResult.Intent.HOUSE_DETAIL);
        assertThat(result.houseId()).isEqualTo(151L);
    }

    @Test
    void recognizesReverseHouseNumberAsDetailIntent() {
        IntentResult result = service.detect("151号房源");

        assertThat(result.intent()).isEqualTo(IntentResult.Intent.HOUSE_DETAIL);
        assertThat(result.houseId()).isEqualTo(151L);
    }

    @Test
    void doesNotTreatLandlordContactRequestAsHouseSearch() {
        IntentResult result = service.detect("帮我联系房东");

        assertThat(result.intent()).isEqualTo(IntentResult.Intent.GENERAL_CHAT);
        assertThat(result.constraints()).isEmpty();
    }

    @Test
    void doesNotTreatContactRequestWithOrdinalReferenceAsHouseSearch() {
        IntentResult result = service.detect("帮我联系第一个房源的房东");

        assertThat(result.intent()).isEqualTo(IntentResult.Intent.GENERAL_CHAT);
        assertThat(result.constraints()).isEmpty();
    }
    @Test
    void inheritsMissingRegionFromPreviousHouseSearchAndReplacesPrice() {
        List<AiModelGateway.ChatTurn> history = List.of(
                new AiModelGateway.ChatTurn("user", "我想在大连找3000元以内的房子"),
                new AiModelGateway.ChatTurn("assistant", "这里是上一轮结果"));

        IntentResult result = service.detect("那2000元以内的呢", history);

        assertThat(result.intent()).isEqualTo(IntentResult.Intent.HOUSE_RECOMMEND);
        assertThat(result.constraints()).anySatisfy(c -> assertConstraint(c,
                SearchConstraint.Field.PRICE, SearchConstraint.Operator.LTE,
                new BigDecimal("2000"), SearchConstraint.Strength.HARD));
        assertThat(result.constraints()).anySatisfy(c -> assertConstraint(c,
                SearchConstraint.Field.REGION, SearchConstraint.Operator.CONTAINS,
                "大连", SearchConstraint.Strength.HARD));
        assertThat(result.constraints()).noneMatch(c -> new BigDecimal("3000").equals(c.value()));
    }

    private void assertConstraint(SearchConstraint actual, SearchConstraint.Field field,
                                  SearchConstraint.Operator operator, Object value,
                                  SearchConstraint.Strength strength) {
        assertThat(actual.field()).isEqualTo(field);
        assertThat(actual.operator()).isEqualTo(operator);
        assertThat(actual.value()).isEqualTo(value);
        assertThat(actual.strength()).isEqualTo(strength);
    }
}
