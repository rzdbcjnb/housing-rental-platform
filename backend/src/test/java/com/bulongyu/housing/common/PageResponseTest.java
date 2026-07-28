package com.bulongyu.housing.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResponseTest {
    @Test
    void copiesResultsToPreventMutation() {
        List<String> source = new ArrayList<>(List.of("one"));
        PageResponse<String> response = new PageResponse<>(1, null, null, source);
        source.add("two");
        assertThat(response.results()).containsExactly("one");
        assertThatThrownBy(() -> response.results().add("three"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
