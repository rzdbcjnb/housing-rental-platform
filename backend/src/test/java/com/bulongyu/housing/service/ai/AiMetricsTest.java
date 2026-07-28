package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.AgentToolTrace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiMetricsTest {
    @Test
    void recordsRouteToolAndStreamMetricsWithoutSensitiveTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics metrics = new AiMetrics(registry);

        metrics.recordRoute("agent");
        metrics.recordTool(new AgentToolTrace("searchHouses", "success", 25, 3));
        metrics.recordFirstDelta(40);
        metrics.recordStream("completed", 80);

        assertThat(registry.get("housing.ai.route.total")
                .tag("route", "agent").counter().count()).isEqualTo(1);
        assertThat(registry.get("housing.ai.tool.total")
                .tag("tool", "searchHouses")
                .tag("status", "success")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("housing.ai.stream.total")
                .tag("status", "completed").counter().count()).isEqualTo(1);
        List<String> tagKeys = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey())
                .toList();
        assertThat(tagKeys).doesNotContain("userId", "query", "token", "content");
    }
}
