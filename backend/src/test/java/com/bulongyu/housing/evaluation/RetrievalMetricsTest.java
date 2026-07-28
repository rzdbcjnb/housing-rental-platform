package com.bulongyu.housing.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalMetricsTest {

    @Test
    void reportsLenientAndStrictMetricsSeparately() {
        RetrievalMetrics.Observation result = new RetrievalMetrics.Observation(
                "Q-001",
                "RESULTS",
                List.of(2L, 3L, 1L),
                Map.of(1L, 2, 2L, 1),
                false,
                12,
                0);

        RetrievalMetrics.Report report = RetrievalMetrics.evaluate(List.of(result));

        assertThat(report.lenient().recallAt().get(10)).isEqualTo(1.0);
        assertThat(report.strict().recallAt().get(10)).isEqualTo(1.0);
        assertThat(report.lenient().precisionAt().get(5)).isEqualTo(0.4);
        assertThat(report.strict().precisionAt().get(5)).isEqualTo(0.2);
        assertThat(report.lenient().mrr()).isEqualTo(1.0);
        assertThat(report.strict().mrr()).isCloseTo(1.0 / 3.0, within(0.000001));
        assertThat(report.ndcgAt().get(10)).isBetween(0.0, 1.0);
    }

    @Test
    void reportsZeroHitHardViolationLatencyAndVectorState() {
        RetrievalMetrics.Observation emptySuccess = new RetrievalMetrics.Observation(
                "ZERO-001", "EMPTY", List.of(), Map.of(), false, 10, 0);
        RetrievalMetrics.Observation emptyFailure = new RetrievalMetrics.Observation(
                "ZERO-002", "EMPTY", List.of(7L, 8L), Map.of(), true, 30, 1);

        RetrievalMetrics.Report report = RetrievalMetrics.evaluate(List.of(emptySuccess, emptyFailure));

        assertThat(report.zeroHitAccuracy()).isEqualTo(0.5);
        assertThat(report.zeroHitFalsePositiveCount()).isEqualTo(2);
        assertThat(report.hardConstraintViolationRate()).isEqualTo(0.5);
        assertThat(report.hardConstraintViolationCount()).isEqualTo(1);
        assertThat(report.vectorActiveRate()).isEqualTo(0.5);
        assertThat(report.averageLatencyMs()).isEqualTo(20.0);
        assertThat(report.p95LatencyMs()).isEqualTo(30);
        assertThat(report.maximumReturnedCount()).isEqualTo(2);
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
