package com.bulongyu.housing.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {
    @Test
    void returnsStableHealthPayload() {
        assertThat(new HealthController().health()).containsEntry("status", "ok");
    }
}
