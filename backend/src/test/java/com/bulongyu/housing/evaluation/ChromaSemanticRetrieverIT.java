package com.bulongyu.housing.evaluation;

import com.bulongyu.housing.HousingRentalPlatformApplication;
import com.bulongyu.housing.service.ai.SemanticRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用真实本地 Embedding 模型和 Chroma 验证房源语义召回链路。
 */
@SpringBootTest(
        classes = HousingRentalPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.ai.model.chat=none",
                "spring.ai.vectorstore.chroma.initialize-schema=false",
                "spring.data.redis.repositories.enabled=false"
        })
class ChromaSemanticRetrieverIT {
    private static final String ROOT = "/evaluation/stage0-v1/";
    private static final List<String> QUERY_IDS = List.of(
            "SEM-001",
            "SEM-005",
            "SEM-009",
            "SEM-013",
            "SEM-017",
            "SEM-021",
            "SEM-025",
            "SEM-029",
            "SEM-033",
            "SEM-037");
    private static final int TOP_K = 10;
    private static final int MINIMUM_HIT_QUERIES = 8;
    private static final Path OUTPUT = Path.of(
            "target", "evaluation", "stage0-v1", "chroma-smoke.json");

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private SemanticRetriever semanticRetriever;
    @Value("${app.ai.retrieval.similarity-threshold}")
    private double similarityThreshold;

    @Test
    void retrievesHumanRelevantHousesThroughRealEmbeddingAndChroma() throws Exception {
        Map<String, JsonNode> queries = queriesById();
        Set<Long> knownHouseIds = knownHouseIds();
        List<Map<String, Object>> observations = new ArrayList<>();
        int hitQueries = 0;
        double recallTotal = 0;
        double reciprocalRankTotal = 0;

        for (String queryId : QUERY_IDS) {
            JsonNode query = queries.get(queryId);
            assertThat(query).as("query %s", queryId).isNotNull();
            Set<Long> relevantIds = relevantIds(query.path("judgments"));
            SemanticRetriever.Retrieval retrieval = semanticRetriever.retrieveHouseIds(
                    query.path("query").asText(),
                    TOP_K);

            assertThat(retrieval.status())
                    .as("retrieval status for %s", queryId)
                    .isEqualTo(SemanticRetriever.RetrievalStatus.SUCCESS_WITH_RESULTS);
            assertThat(retrieval.ids())
                    .as("ranked ids for %s", queryId)
                    .isNotEmpty()
                    .hasSizeLessThanOrEqualTo(TOP_K)
                    .doesNotHaveDuplicates()
                    .allMatch(knownHouseIds::contains);

            int relevantCount = 0;
            int firstRelevantRank = 0;
            for (int index = 0; index < retrieval.ids().size(); index++) {
                if (relevantIds.contains(retrieval.ids().get(index))) {
                    relevantCount++;
                    if (firstRelevantRank == 0) {
                        firstRelevantRank = index + 1;
                    }
                }
            }
            if (firstRelevantRank > 0) {
                hitQueries++;
                reciprocalRankTotal += 1.0 / firstRelevantRank;
            }
            double recallAtTen = relevantIds.isEmpty()
                    ? 0
                    : (double) relevantCount / relevantIds.size();
            recallTotal += recallAtTen;

            Map<String, Object> observation = new LinkedHashMap<>();
            observation.put("query_id", queryId);
            observation.put("query", query.path("query").asText());
            observation.put("status", retrieval.status().name());
            observation.put("ranked_house_ids", retrieval.ids());
            observation.put("scores", retrieval.scores());
            observation.put("relevant_hit_count", relevantCount);
            observation.put("first_relevant_rank", firstRelevantRank);
            observation.put("recall_at_10", recallAtTen);
            observations.add(observation);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", OffsetDateTime.now().toString());
        report.put("mode", "REAL_LOCAL_BGE_CHROMA_SMOKE");
        report.put("collection", "SpringAiTenant/SpringAiDatabase/housing-rag");
        report.put("similarity_threshold", similarityThreshold);
        report.put("query_count", QUERY_IDS.size());
        report.put("successful_vector_queries", observations.size());
        report.put("top_10_hit_queries", hitQueries);
        report.put("hit_query_rate", (double) hitQueries / QUERY_IDS.size());
        report.put("mean_recall_at_10", recallTotal / QUERY_IDS.size());
        report.put("mrr", reciprocalRankTotal / QUERY_IDS.size());
        report.put("observations", observations);
        writeReport(report);

        assertThat(hitQueries)
                .as("queries with at least one human-relevant house in Top10")
                .isGreaterThanOrEqualTo(MINIMUM_HIT_QUERIES);
    }

    private Map<String, JsonNode> queriesById() throws IOException {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (JsonNode query : jsonLines("queries.jsonl")) {
            values.put(query.path("query_id").asText(), query);
        }
        return values;
    }

    private Set<Long> knownHouseIds() throws IOException {
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode house : jsonLines("houses.jsonl")) {
            ids.add(house.path("house_id").asLong());
        }
        assertThat(ids).hasSize(110);
        return Set.copyOf(ids);
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

    private List<JsonNode> jsonLines(String name) throws IOException {
        String content;
        try (InputStream stream = ChromaSemanticRetrieverIT.class.getResourceAsStream(ROOT + name)) {
            assertThat(stream).as("resource %s", name).isNotNull();
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<JsonNode> values = new ArrayList<>();
        for (String line : content.lines().toList()) {
            if (!line.isBlank()) {
                values.add(json.readTree(line));
            }
        }
        return List.copyOf(values);
    }

    private void writeReport(Map<String, Object> report) throws IOException {
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(
                OUTPUT,
                json.writeValueAsString(report) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }
}
