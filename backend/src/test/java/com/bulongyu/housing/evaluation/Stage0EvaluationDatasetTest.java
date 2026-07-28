package com.bulongyu.housing.evaluation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Stage0EvaluationDatasetTest {
    private static final String ROOT = "/evaluation/stage0-v1/";
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void snapshotContainsTheConfirmedPublicHouseSet() throws IOException {
        List<JsonNode> houses = jsonLines("houses.jsonl");

        assertThat(houses).hasSize(110);
        assertThat(houses)
                .allSatisfy(house -> {
                    assertThat(house.path("dataset_version").asText()).isEqualTo("stage0-v1");
                    assertThat(house.path("status").asText()).isEqualTo("approved");
                    assertThat(house.path("is_active").asBoolean()).isTrue();
                });
        assertThat(houses.stream()
                .filter(house -> contains(house.path("data_quality_flags"),
                        "REGION_HIERARCHY_INCOMPLETE")))
                .hasSize(20);
        assertThat(houses.stream().map(house -> house.path("house_id").asLong()).distinct())
                .hasSize(110);
    }

    @Test
    void queriesFollowTheConfirmedDistributionAndReferenceKnownHouses() throws IOException {
        List<JsonNode> houses = jsonLines("houses.jsonl");
        List<JsonNode> queries = jsonLines("queries.jsonl");
        Set<Long> houseIds = new HashSet<>();
        houses.forEach(house -> houseIds.add(house.path("house_id").asLong()));

        assertThat(queries).hasSize(150);
        assertThat(countBy(queries, "category")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "STRUCTURED", 45L,
                "SEMANTIC", 40L,
                "MIXED", 40L,
                "ZERO_HIT", 20L,
                "CONFLICT_OR_DIRTY", 5L));
        assertThat(countBy(queries, "label_status")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "AUTO_DERIVED", 60L,
                "HUMAN_CONFIRMED", 90L));

        Set<String> queryIds = new HashSet<>();
        Set<Integer> grades = new HashSet<>();
        for (JsonNode query : queries) {
            assertThat(queryIds.add(query.path("query_id").asText())).isTrue();
            assertThat(query.path("dataset_version").asText()).isEqualTo("stage0-v1");
            assertOutcomeContract(query);
            for (JsonNode judgment : query.path("judgments")) {
                assertThat(houseIds).contains(judgment.path("house_id").asLong());
                int grade = judgment.path("relevance").asInt();
                assertThat(grade).isBetween(1, 2);
                grades.add(grade);
            }
        }
        assertThat(grades).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void manifestHashesMatchTheVersionedJsonlFiles() throws Exception {
        JsonNode manifest = json.readTree(resourceBytes("manifest.json"));

        assertThat(manifest.path("dataset_version").asText()).isEqualTo("stage0-v1");
        assertThat(manifest.path("house_count").asInt()).isEqualTo(110);
        assertThat(manifest.path("query_count").asInt()).isEqualTo(150);
        assertThat(manifest.path("incomplete_region_count").asInt()).isEqualTo(20);
        assertThat(manifest.path("houses_sha256").asText())
                .isEqualTo(sha256(resourceBytes("houses.jsonl")));
        assertThat(manifest.path("queries_sha256").asText())
                .isEqualTo(sha256(resourceBytes("queries.jsonl")));
    }

    private void assertOutcomeContract(JsonNode query) {
        String outcome = query.path("expected_outcome").asText();
        int judgmentCount = query.path("judgments").size();
        if ("RESULTS".equals(outcome)) {
            assertThat(judgmentCount).isPositive();
        }
        else if ("EMPTY".equals(outcome) || "INVALID_QUERY".equals(outcome)) {
            assertThat(judgmentCount).isZero();
        }
        else {
            assertThat(outcome).isEqualTo("UNSUPPORTED");
            assertThat(query.path("supported").asBoolean()).isFalse();
        }
    }

    private Map<String, Long> countBy(List<JsonNode> values, String field) {
        Map<String, Long> counts = new HashMap<>();
        for (JsonNode value : values) {
            counts.merge(value.path(field).asText(), 1L, Long::sum);
        }
        return counts;
    }

    private boolean contains(JsonNode array, String expected) {
        for (JsonNode value : array) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private List<JsonNode> jsonLines(String name) throws IOException {
        String content = new String(resourceBytes(name), StandardCharsets.UTF_8);
        List<JsonNode> values = new ArrayList<>();
        for (String line : content.lines().toList()) {
            if (!line.isBlank()) {
                values.add(json.readTree(line));
            }
        }
        return List.copyOf(values);
    }

    private byte[] resourceBytes(String name) throws IOException {
        try (InputStream stream = Stage0EvaluationDatasetTest.class.getResourceAsStream(ROOT + name)) {
            assertThat(stream).as("resource %s", name).isNotNull();
            return stream.readAllBytes();
        }
    }

    private String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte current : digest) {
            value.append(String.format(Locale.ROOT, "%02x", current));
        }
        return value.toString();
    }
}
