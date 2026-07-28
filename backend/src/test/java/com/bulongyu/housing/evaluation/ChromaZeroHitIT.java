package com.bulongyu.housing.evaluation;

import com.bulongyu.housing.HousingRentalPlatformApplication;
import com.bulongyu.housing.service.ai.SemanticRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证真实向量链路不会为不存在的特殊设施制造假推荐。
 */
@SpringBootTest(
        classes = HousingRentalPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.ai.model.chat=none",
                "spring.ai.vectorstore.chroma.initialize-schema=false",
                "spring.data.redis.repositories.enabled=false"
        })
class ChromaZeroHitIT {
    private static final String QUERIES = "/evaluation/stage0-v1/queries.jsonl";
    private static final Path OUTPUT = Path.of(
            "target", "evaluation", "stage0-v1", "chroma-zero-hit.json");

    @Autowired
    private SemanticRetriever retriever;
    @Value("${app.ai.retrieval.similarity-threshold}")
    private double similarityThreshold;

    @Test
    void returnsNoHousesForHumanConfirmedSemanticZeroHitQueries() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        List<Map<String, Object>> observations = new ArrayList<>();
        int correctEmpty = 0;
        try (InputStream stream = ChromaZeroHitIT.class.getResourceAsStream(QUERIES)) {
            assertThat(stream).isNotNull();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : content.lines().toList()) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode query = json.readTree(line);
                if (!"ZERO_HIT".equals(query.path("category").asText())
                        || !"HUMAN_CONFIRMED".equals(query.path("label_status").asText())) {
                    continue;
                }
                SemanticRetriever.Retrieval result = retriever.retrieveHouseIds(
                        query.path("query").asText(), 10);
                if (result.status() == SemanticRetriever.RetrievalStatus.SUCCESS_EMPTY) {
                    correctEmpty++;
                }
                Map<String, Object> observation = new LinkedHashMap<>();
                observation.put("query_id", query.path("query_id").asText());
                observation.put("query", query.path("query").asText());
                observation.put("status", result.status().name());
                observation.put("house_ids", result.ids());
                observation.put("scores", result.scores());
                observations.add(observation);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", OffsetDateTime.now().toString());
        report.put("mode", "REAL_LOCAL_BGE_CHROMA_SEMANTIC_ZERO_HIT");
        report.put("similarity_threshold", similarityThreshold);
        report.put("query_count", observations.size());
        report.put("correct_empty_queries", correctEmpty);
        report.put("zero_hit_accuracy", observations.isEmpty()
                ? 0
                : (double) correctEmpty / observations.size());
        report.put("observations", observations);
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(
                OUTPUT,
                json.writeValueAsString(report) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        assertThat(observations).hasSize(10);
        assertThat(correctEmpty)
                .as("human-confirmed semantic zero-hit queries returning no houses")
                .isEqualTo(observations.size());
    }
}
