package com.bulongyu.housing.evaluation;

import com.bulongyu.housing.HousingRentalPlatformApplication;
import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import com.bulongyu.housing.service.ai.AiHouseSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = HousingRentalPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.ai.model.chat=none",
                "spring.ai.model.embedding=none",
                "spring.ai.vectorstore.type=none",
                "spring.ai.vectorstore.chroma.enabled=false",
                "spring.data.redis.repositories.enabled=false"
        })
class Stage0CurrentBaselineIT {
    private static final String ROOT = "/evaluation/stage0-v1/";
    private static final Path OUTPUT = Path.of("target", "evaluation", "stage0-v1");

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private AiHouseSearchService houseSearchService;

    @Test
    void writesMysqlFallbackBaselineReport() throws Exception {
        Map<Long, JsonNode> houses = housesById();
        List<JsonNode> queries = jsonLines("queries.jsonl");
        List<RetrievalMetrics.Observation> observations = new ArrayList<>();
        List<Map<String, Object>> predictions = new ArrayList<>();

        for (JsonNode query : queries) {
            String expectedOutcome = query.path("expected_outcome").asText();
            if (!query.path("supported").asBoolean()
                    || "INVALID_QUERY".equals(expectedOutcome)) {
                continue;
            }
            List<SearchConstraint> constraints = constraints(query.path("constraints"));
            IntentResult intent = new IntentResult(
                    IntentResult.Intent.HOUSE_RECOMMEND,
                    constraints,
                    null,
                    query.path("query").asText(),
                    "");
            long startedAt = System.nanoTime();
            AiHouseSearchService.SearchResult result = houseSearchService.search(intent, 50);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            List<Long> rankedIds = result.houses().stream().map(house -> house.id()).toList();
            assertThat(rankedIds).allSatisfy(id -> assertThat(houses).containsKey(id));
            int hardViolations = hardViolationCount(rankedIds, houses, query.path("constraints"));
            Map<Long, Integer> judgments = judgments(query.path("judgments"));
            observations.add(new RetrievalMetrics.Observation(
                    query.path("query_id").asText(),
                    expectedOutcome,
                    rankedIds,
                    judgments,
                    result.vectorActive(),
                    latencyMs,
                    hardViolations));
            predictions.add(prediction(
                    query.path("query_id").asText(),
                    expectedOutcome,
                    rankedIds,
                    result.status(),
                    result.vectorActive(),
                    latencyMs,
                    hardViolations));
        }

        assertThat(observations).hasSize(145);
        assertThat(observations).noneMatch(RetrievalMetrics.Observation::vectorActive);
        RetrievalMetrics.Report report = RetrievalMetrics.evaluate(observations);
        assertThat(report.maximumReturnedCount()).isEqualTo(50);
        assertThat(observations).anyMatch(observation -> observation.rankedIds().size() > 20);
        Map<String, Long> statusCounts = statusCounts(predictions);
        Files.createDirectories(OUTPUT);
        writePredictions(predictions);
        Files.writeString(
                OUTPUT.resolve("mysql-fallback-baseline.json"),
                json.writeValueAsString(reportJson(report, statusCounts)) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(
                OUTPUT.resolve("mysql-fallback-baseline.md"),
                markdown(report, statusCounts),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private Map<String, Object> prediction(String queryId,
                                           String expectedOutcome,
                                           List<Long> rankedIds,
                                           AiHouseSearchService.SearchStatus status,
                                           boolean vectorActive,
                                           long latencyMs,
                                           int hardViolations) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("query_id", queryId);
        value.put("expected_outcome", expectedOutcome);
        value.put("ranked_house_ids", rankedIds);
        value.put("search_status", status.name());
        value.put("vector_active", vectorActive);
        value.put("latency_ms", latencyMs);
        value.put("hard_constraint_violations", hardViolations);
        return value;
    }

    private Map<String, Long> statusCounts(List<Map<String, Object>> predictions) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AiHouseSearchService.SearchStatus status : AiHouseSearchService.SearchStatus.values()) {
            long count = predictions.stream()
                    .filter(prediction -> status.name().equals(prediction.get("search_status")))
                    .count();
            counts.put(status.name(), count);
        }
        return counts;
    }

    private void writePredictions(List<Map<String, Object>> predictions) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                OUTPUT.resolve("mysql-fallback-predictions.jsonl"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Map<String, Object> prediction : predictions) {
                writer.write(json.writeValueAsString(prediction));
                writer.newLine();
            }
        }
    }

    private Map<String, Object> reportJson(RetrievalMetrics.Report report,
                                                   Map<String, Long> statusCounts) throws IOException {
        JsonNode manifest = json.readTree(resourceBytes("manifest.json"));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("dataset_version", manifest.path("dataset_version").asText());
        value.put("queries_sha256", manifest.path("queries_sha256").asText());
        value.put("generated_at", OffsetDateTime.now().toString());
        value.put("baseline_mode", "MYSQL_FALLBACK_VECTOR_DISABLED");
        value.put("metric_policy", "M3_K1");
        value.put("executed_queries", report.executedQueries());
        value.put("result_queries", report.resultQueries());
        value.put("empty_queries", report.emptyQueries());
        value.put("search_status_counts", statusCounts);
        value.put("lenient_grade_gte_1", binaryJson(report.lenient()));
        value.put("strict_grade_eq_2", binaryJson(report.strict()));
        value.put("graded_ndcg_at", report.ndcgAt());
        value.put("zero_hit_accuracy", report.zeroHitAccuracy());
        value.put("zero_hit_false_positive_count", report.zeroHitFalsePositiveCount());
        value.put("hard_constraint_violation_rate", report.hardConstraintViolationRate());
        value.put("hard_constraint_violation_count", report.hardConstraintViolationCount());
        value.put("returned_house_count", report.returnedHouseCount());
        value.put("vector_active_rate", report.vectorActiveRate());
        value.put("average_latency_ms", report.averageLatencyMs());
        value.put("p95_latency_ms", report.p95LatencyMs());
        value.put("maximum_returned_count", report.maximumReturnedCount());
        return value;
    }

    private Map<String, Object> binaryJson(RetrievalMetrics.BinaryMetrics metrics) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("recall_at", metrics.recallAt());
        value.put("precision_at", metrics.precisionAt());
        value.put("mrr", metrics.mrr());
        return value;
    }

    private String markdown(RetrievalMetrics.Report report,
                            Map<String, Long> statusCounts) throws IOException {
        JsonNode manifest = json.readTree(resourceBytes("manifest.json"));
        StringBuilder value = new StringBuilder();
        value.append("# Stage 0 MySQL 降级检索基线\n\n")
                .append("> 数据集：").append(manifest.path("dataset_version").asText()).append("  \n")
                .append("> 查询哈希：`").append(manifest.path("queries_sha256").asText()).append("`  \n")
                .append("> 模式：`MYSQL_FALLBACK_VECTOR_DISABLED`  \n")
                .append("> 指标口径：`M3 + K1`\n\n")
                .append("## 运行范围\n\n")
                .append("- 已执行查询：").append(report.executedQueries()).append("\n")
                .append("- 有结果标注查询：").append(report.resultQueries()).append("\n")
                .append("- 零命中查询：").append(report.emptyQueries()).append("\n")
                .append("- 向量检索激活率：").append(percent(report.vectorActiveRate())).append("\n")
                .append("- 单次最多返回：").append(report.maximumReturnedCount()).append(" 套\n")
                .append("- 检索状态分布：").append(statusCounts).append("\n\n")
                .append("## M3 检索指标\n\n")
                .append("| 口径 | Recall@10 | Recall@20 | Recall@50 | Precision@5 | Precision@10 | MRR |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|\n")
                .append(binaryRow("宽松（相关性 >= 1）", report.lenient()))
                .append(binaryRow("严格（相关性 = 2）", report.strict()))
                .append("\n| nDCG@10 | nDCG@20 |\n")
                .append("|---:|---:|\n")
                .append("|").append(decimal(report.ndcgAt().get(10))).append("|")
                .append(decimal(report.ndcgAt().get(20))).append("|\n\n")
                .append("## 正确性与运行状态\n\n")
                .append("- 零命中正确率：").append(percent(report.zeroHitAccuracy())).append("\n")
                .append("- 零命中错误返回房源数：").append(report.zeroHitFalsePositiveCount()).append("\n")
                .append("- 硬条件违规率：").append(percent(report.hardConstraintViolationRate()))
                .append("（").append(report.hardConstraintViolationCount()).append("/")
                .append(report.returnedHouseCount()).append("）\n")
                .append("- 平均检索耗时：").append(decimal(report.averageLatencyMs())).append(" ms\n")
                .append("- P95 检索耗时：").append(report.p95LatencyMs()).append(" ms\n\n")
                .append("## 解释限制\n\n")
                .append("- 本报告强制关闭向量库，只量化当前 MySQL 降级路径，不能代表 Chroma 正常时的语义召回质量。\n")
                .append("- 统一检索内部最多返回 50 套，Agent/UI 仍由调用层限制为最多 20 套。\n")
                .append("- 向量库不可用时，纯语义查询返回 RETRIEVAL_UNAVAILABLE，不以任意房源制造假命中。\n")
                .append("- Chroma 恢复后必须使用同一数据集另行生成向量正常基线。\n");
        return value.toString();
    }

    private String binaryRow(String label, RetrievalMetrics.BinaryMetrics metrics) {
        return "|" + label
                + "|" + decimal(metrics.recallAt().get(10))
                + "|" + decimal(metrics.recallAt().get(20))
                + "|" + decimal(metrics.recallAt().get(50))
                + "|" + decimal(metrics.precisionAt().get(5))
                + "|" + decimal(metrics.precisionAt().get(10))
                + "|" + decimal(metrics.mrr()) + "|\n";
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100);
    }

    private Map<Long, JsonNode> housesById() throws IOException {
        Map<Long, JsonNode> houses = new LinkedHashMap<>();
        for (JsonNode house : jsonLines("houses.jsonl")) {
            houses.put(house.path("house_id").asLong(), house);
        }
        assertThat(houses).hasSize(110);
        return houses;
    }

    private List<SearchConstraint> constraints(JsonNode values) {
        List<SearchConstraint> constraints = new ArrayList<>();
        for (JsonNode value : values) {
            SearchConstraint.Field field = SearchConstraint.Field.valueOf(value.path("field").asText());
            Object typedValue = switch (field) {
                case REGION -> value.path("value").asText();
                case PRICE -> value.path("value").decimalValue();
                default -> value.path("value").asInt();
            };
            constraints.add(new SearchConstraint(
                    field,
                    SearchConstraint.Operator.valueOf(value.path("operator").asText()),
                    typedValue,
                    SearchConstraint.Strength.valueOf(value.path("strength").asText())));
        }
        return List.copyOf(constraints);
    }

    private Map<Long, Integer> judgments(JsonNode values) {
        Map<Long, Integer> judgments = new LinkedHashMap<>();
        for (JsonNode value : values) {
            judgments.put(value.path("house_id").asLong(), value.path("relevance").asInt());
        }
        return Map.copyOf(judgments);
    }

    private int hardViolationCount(List<Long> rankedIds,
                                   Map<Long, JsonNode> houses,
                                   JsonNode constraints) {
        int violations = 0;
        for (Long houseId : rankedIds) {
            JsonNode house = houses.get(houseId);
            boolean matches = true;
            for (JsonNode constraint : constraints) {
                if ("HARD".equals(constraint.path("strength").asText())
                        && !matches(house, constraint)) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                violations++;
            }
        }
        return violations;
    }

    private boolean matches(JsonNode house, JsonNode constraint) {
        String field = constraint.path("field").asText();
        String operator = constraint.path("operator").asText();
        JsonNode expected = constraint.path("value");
        return switch (field) {
            case "REGION" -> fullRegion(house).contains(expected.asText().toLowerCase(Locale.ROOT));
            case "PRICE" -> compare(house.path("price").decimalValue(), expected.decimalValue(), operator);
            case "BEDROOMS" -> compare(house.path("bedroom_count").asInt(), expected.asInt(), operator);
            case "LIVING_ROOMS" -> compare(house.path("living_room_count").asInt(), expected.asInt(), operator);
            case "BATHROOMS" -> !house.path("bathroom_count").isNull()
                    && compare(house.path("bathroom_count").asInt(), expected.asInt(), operator);
            case "KITCHENS" -> !house.path("kitchen_count").isNull()
                    && compare(house.path("kitchen_count").asInt(), expected.asInt(), operator);
            default -> false;
        };
    }

    private boolean compare(BigDecimal actual, BigDecimal expected, String operator) {
        int result = actual.compareTo(expected);
        return "EQ".equals(operator) ? result == 0
                : "GTE".equals(operator) ? result >= 0
                : result <= 0;
    }

    private boolean compare(int actual, int expected, String operator) {
        return "EQ".equals(operator) ? actual == expected
                : "GTE".equals(operator) ? actual >= expected
                : actual <= expected;
    }

    private String fullRegion(JsonNode house) {
        return (house.path("city").asText()
                + house.path("district").asText()
                + house.path("street").asText()).toLowerCase(Locale.ROOT);
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
        try (InputStream stream = Stage0CurrentBaselineIT.class.getResourceAsStream(ROOT + name)) {
            assertThat(stream).as("resource %s", name).isNotNull();
            return stream.readAllBytes();
        }
    }
}
