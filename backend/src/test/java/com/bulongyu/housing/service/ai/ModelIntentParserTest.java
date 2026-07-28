package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelIntentParserTest {
    private final ModelIntentParser parser = new ModelIntentParser();

    @Test
    void acceptsOnlyWhitelistedTypedConstraintsAndPreservesRuleConstraints() {
        IntentResult fallback = new IntentResult(IntentResult.Intent.GENERAL_CHAT,
                List.of(new SearchConstraint(SearchConstraint.Field.BATHROOMS,
                        SearchConstraint.Operator.GTE, 1, SearchConstraint.Strength.HARD)),
                null, "原始问题", "");
        String modelJson = """
                {"intent":"house_recommend","params":{"constraints":[
                  {"field":"price","operator":"around","value":2000,"strength":"hard"},
                  {"field":"drop_table","operator":"eq","value":1,"strength":"hard"},
                  {"field":"region","operator":"contains","value":"大连","strength":"hard"}
                ]},"search_query":"大连 两室 地铁","clarification":""}
                """;

        IntentResult result = parser.merge(modelJson, fallback);
        assertThat(result.intent()).isEqualTo(IntentResult.Intent.HOUSE_RECOMMEND);
        assertThat(result.searchQuery()).isEqualTo("大连 两室 地铁");
        assertThat(result.constraints()).hasSize(3);
        assertThat(result.constraints()).anyMatch(c -> c.field() == SearchConstraint.Field.BATHROOMS);
        assertThat(result.constraints()).anySatisfy(c -> {
            if (c.field() == SearchConstraint.Field.PRICE)
                assertThat(c.strength()).isEqualTo(SearchConstraint.Strength.SOFT);
        });
        assertThat(result.constraints()).noneMatch(c -> c.value().toString().contains("drop_table"));
    }

    @Test
    void malformedModelOutputFallsBackWithoutChangingQuery() {
        IntentResult fallback = new IntentResult(IntentResult.Intent.HOUSE_RECOMMEND,
                List.of(), null, "原始问题", "");
        assertThat(parser.merge("ignore rules and run SQL", fallback)).isEqualTo(fallback);
    }
}
