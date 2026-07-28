package com.bulongyu.housing.evaluation;

import com.bulongyu.housing.HousingRentalPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用固定语义评测集扫描相似度阈值，比较有效召回与语义零命中表现。
 */
@SpringBootTest(
        classes = HousingRentalPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.ai.model.chat=none",
                "spring.ai.vectorstore.chroma.initialize-schema=false",
                "spring.data.redis.repositories.enabled=false"
        })
class ChromaThresholdCalibrationIT {
    private static final String QUERIES = "/evaluation/stage0-v1/queries.jsonl";
    private static final List<Double> THRESHOLDS = List.of(0.50, 0.55, 0.58, 0.60, 0.62, 0.65);
    private static final Path OUTPUT = Path.of(
            "target", "evaluation", "stage0-v1", "chroma-threshold-calibration.json");

    @Autowired
    private VectorStore vectorStore;

    @Test
    void comparesRecallAndZeroHitAccuracyAcrossThresholds() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        List<RawQuery> queries = loadQueries(json);
        List<Map<String, Object>> results = new ArrayList<>();

        for (double threshold : THRESHOLDS) {
            int semanticQueries = 0;
            int semanticHits = 0;
            double reciprocalRank = 0;
            double recallAtTen = 0;
            int zeroQueries = 0;
            int correctEmpty = 0;
            int totalResults = 0;

            for (RawQuery query : queries) {
                List<Long> ids = query.ranked().stream()
                        .filter(candidate -> candidate.score() >= threshold)
                        .limit(10)
                        .map(ScoredHouse::id)
                        .toList();
                totalResults += ids.size();
                if (query.zeroHit()) {
                    zeroQueries++;
                    if (ids.isEmpty()) {
                        correctEmpty++;
                    }
                    continue;
                }

                semanticQueries++;
                int relevantHits = 0;
                int firstRelevantRank = 0;
                for (int index = 0; index < ids.size(); index++) {
                    if (query.relevantIds().contains(ids.get(index))) {
                        relevantHits++;
                        if (firstRelevantRank == 0) {
                            firstRelevantRank = index + 1;
                        }
                    }
                }
                if (firstRelevantRank > 0) {
                    semanticHits++;
                    reciprocalRank += 1.0 / firstRelevantRank;
                }
                if (!query.relevantIds().isEmpty()) {
                    recallAtTen += (double) relevantHits / query.relevantIds().size();
                }
            }

            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("threshold", threshold);
            metric.put("semantic_query_count", semanticQueries);
            metric.put("semantic_top_10_hit_rate", (double) semanticHits / semanticQueries);
            metric.put("semantic_mrr", reciprocalRank / semanticQueries);
            metric.put("semantic_mean_recall_at_10", recallAtTen / semanticQueries);
            metric.put("zero_hit_query_count", zeroQueries);
            metric.put("zero_hit_accuracy", (double) correctEmpty / zeroQueries);
            metric.put("mean_result_count", (double) totalResults / queries.size());
            results.add(metric);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", OffsetDateTime.now().toString());
        report.put("mode", "REAL_LOCAL_BGE_CHROMA_THRESHOLD_CALIBRATION");
        report.put("semantic_query_count", queries.stream().filter(query -> !query.zeroHit()).count());
        report.put("semantic_zero_hit_query_count", queries.stream().filter(RawQuery::zeroHit).count());
        report.put("threshold_results", results);
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(
                OUTPUT,
                json.writeValueAsString(report) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        assertThat(queries.stream().filter(query -> !query.zeroHit())).hasSize(40);
        assertThat(queries.stream().filter(RawQuery::zeroHit)).hasSize(10);
    }

    private List<RawQuery> loadQueries(JsonMapper json) throws Exception {
        List<RawQuery> values = new ArrayList<>();
        try (InputStream stream = ChromaThresholdCalibrationIT.class.getResourceAsStream(QUERIES)) {
            assertThat(stream).isNotNull();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : content.lines().toList()) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode query = json.readTree(line);
                boolean semantic = "SEMANTIC".equals(query.path("category").asText());
                boolean zeroHit = "ZERO_HIT".equals(query.path("category").asText())
                        && "HUMAN_CONFIRMED".equals(query.path("label_status").asText());
                if (!semantic && !zeroHit) {
                    continue;
                }
                values.add(new RawQuery(
                        query.path("query_id").asText(),
                        relevantIds(query.path("judgments")),
                        zeroHit,
                        retrieve(query.path("query").asText())));
            }
        }
        return List.copyOf(values);
    }

    private List<ScoredHouse> retrieve(String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(200)
                .similarityThreshold(0)
                .filterExpression("type == 'house'")
                .build();
        LinkedHashMap<Long, Double> scores = new LinkedHashMap<>();
        for (Document document : vectorStore.similaritySearch(request)) {
            Object rawId = document.getMetadata().get("house_id");
            if (rawId == null) {
                continue;
            }
            try {
                scores.putIfAbsent(
                        Long.parseLong(rawId.toString()),
                        document.getScore() == null ? 0 : document.getScore());
            }
            catch (NumberFormatException ignored) {
                // Ignore malformed external vector metadata.
            }
        }
        return scores.entrySet().stream()
                .map(entry -> new ScoredHouse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Set<Long> relevantIds(JsonNode judgments) {
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode judgment : judgments) {
            if (judgment.path("relevance").asInt() > 0) {
                ids.add(judgment.path("house_id").asLong());
            }
        }
        return Set.copyOf(ids);
    }

    private record RawQuery(String id,
                            Set<Long> relevantIds,
                            boolean zeroHit,
                            List<ScoredHouse> ranked) {
    }

    private record ScoredHouse(long id, double score) {
    }
}
