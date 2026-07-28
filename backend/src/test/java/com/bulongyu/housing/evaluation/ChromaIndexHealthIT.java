package com.bulongyu.housing.evaluation;

import com.bulongyu.housing.HousingRentalPlatformApplication;
import com.bulongyu.housing.entity.HouseQuery;
import com.bulongyu.housing.mapper.HouseMapper;
import com.bulongyu.housing.service.ai.KnowledgeIndexService;
import com.bulongyu.housing.service.ai.RagVectorHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 显式执行真实 MySQL 到 Chroma 的全量同步，并验收索引水位健康状态。
 * 此测试会修改本地 housing-rag Collection，不属于默认测试套件。
 */
@SpringBootTest(
        classes = HousingRentalPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.ai.model.chat=none",
                "spring.ai.vectorstore.chroma.initialize-schema=false",
                "spring.data.redis.repositories.enabled=false"
        })
class ChromaIndexHealthIT {
    private static final HouseQuery ALL = new HouseQuery(null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

    @Autowired
    private ChromaApi chromaApi;
    @Autowired
    private ChromaVectorStoreProperties chromaProperties;
    @Autowired
    private HouseMapper houses;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private KnowledgeIndexService indexService;
    @Autowired
    private RagVectorHealthIndicator healthIndicator;

    @Test
    void synchronizesIndexMarkerAndMakesRealCollectionReady() {
        long houseCount = houses.countPublic(ALL);
        ChromaApi.Collection collection = chromaApi.getCollection(
                chromaProperties.getTenantName(),
                chromaProperties.getDatabaseName(),
                chromaProperties.getCollectionName());
        long before = chromaApi.countEmbeddings(
                chromaProperties.getTenantName(),
                chromaProperties.getDatabaseName(),
                collection.id());
        long legacyExpected = KnowledgeIndexService.expectedDocumentCount(houseCount) - 1;
        long currentExpected = KnowledgeIndexService.expectedDocumentCount(houseCount);
        assertThat(before)
                .as("only a known legacy or current index may be replaced")
                .isIn(legacyExpected, currentExpected);

        Long adminUserId = jdbc.queryForObject("""
                SELECT u.id
                FROM auth_user u
                JOIN user_profile p ON p.user_id = u.id
                WHERE p.role = 'admin'
                ORDER BY u.id
                LIMIT 1
                """, Long.class);
        assertThat(adminUserId).isNotNull();

        KnowledgeIndexService.IndexResult result = indexService.syncAll(adminUserId);

        assertThat(result.houses()).isEqualTo(houseCount);
        assertThat(result.houseDocuments()).isEqualTo(houseCount * 4);
        assertThat(result.faqDocuments()).isEqualTo(7);
        assertThat(chromaApi.countEmbeddings(
                chromaProperties.getTenantName(),
                chromaProperties.getDatabaseName(),
                collection.id())).isEqualTo(currentExpected);
        Health health = healthIndicator.health();
        assertThat(health.getStatus()).as("health=%s", health).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("document_count", currentExpected)
                .containsEntry("synchronization", "CURRENT");
    }
}
