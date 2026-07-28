package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.mapper.HouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagVectorHealthIndicatorTest {
    private static final LocalDateTime SOURCE_UPDATED_AT = LocalDateTime.of(2026, 7, 27, 12, 30);

    private ObjectProvider<VectorStore> vectorStores;
    private ObjectProvider<ChromaApi> chromaApis;
    private ObjectProvider<ChromaVectorStoreProperties> chromaProperties;
    private HouseMapper houses;
    private VectorStore vectorStore;
    private ChromaApi api;
    private ChromaVectorStoreProperties properties;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        vectorStores = mock(ObjectProvider.class);
        chromaApis = mock(ObjectProvider.class);
        chromaProperties = mock(ObjectProvider.class);
        houses = mock(HouseMapper.class);
        vectorStore = mock(VectorStore.class);
        api = mock(ChromaApi.class);
        properties = new ChromaVectorStoreProperties();
        properties.setTenantName("SpringAiTenant");
        properties.setDatabaseName("SpringAiDatabase");
        properties.setCollectionName("housing-rag");
    }

    @Test
    void reportsOutOfServiceWhenVectorStoreIsDisabled() {
        when(vectorStores.getIfAvailable()).thenReturn(null);

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("reason", "VECTOR_STORE_UNAVAILABLE");
        verifyNoInteractions(api, houses);
    }

    @Test
    void reportsOutOfServiceWhenCollectionIsMissing() {
        when(vectorStores.getIfAvailable()).thenReturn(vectorStore);
        when(chromaApis.getIfAvailable()).thenReturn(api);
        when(chromaProperties.getIfAvailable()).thenReturn(properties);
        when(api.listCollections("SpringAiTenant", "SpringAiDatabase")).thenReturn(List.of());

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("reason", "COLLECTION_MISSING");
        verifyNoInteractions(houses);
    }

    @Test
    void reportsOutOfServiceWhenCollectionIsEmpty() {
        prepareCollection(0L, null);

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("reason", "COLLECTION_EMPTY");
        verifyNoInteractions(houses);
    }

    @Test
    void reportsUpWhenCollectionAndSourceWatermarkMatch() {
        prepareCollection(448L, marker(110, 448, SOURCE_UPDATED_AT));
        when(houses.countPublic(any())).thenReturn(110L);
        when(houses.latestPublicUpdateTime()).thenReturn(SOURCE_UPDATED_AT);

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("document_count", 448L)
                .containsEntry("source_house_count", 110L)
                .containsEntry("synchronization", "CURRENT")
                .containsEntry("index_schema_version", KnowledgeIndexService.INDEX_SCHEMA_VERSION);
    }

    @Test
    void reportsOutOfServiceWhenContentChangedWithoutChangingHouseCount() {
        prepareCollection(448L, marker(110, 448, SOURCE_UPDATED_AT.minusMinutes(1)));
        when(houses.countPublic(any())).thenReturn(110L);
        when(houses.latestPublicUpdateTime()).thenReturn(SOURCE_UPDATED_AT);

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("reason", "INDEX_STALE");
    }

    @Test
    void reportsDownWhenChromaCannotBeReached() {
        when(vectorStores.getIfAvailable()).thenReturn(vectorStore);
        when(chromaApis.getIfAvailable()).thenReturn(api);
        when(chromaProperties.getIfAvailable()).thenReturn(properties);
        when(api.listCollections(any(), any())).thenThrow(new IllegalStateException("offline"));

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("reason", "VECTOR_HEALTH_CHECK_FAILED")
                .containsEntry("exception", "IllegalStateException");
    }

    private void prepareCollection(long documentCount, Document marker) {
        when(vectorStores.getIfAvailable()).thenReturn(vectorStore);
        when(chromaApis.getIfAvailable()).thenReturn(api);
        when(chromaProperties.getIfAvailable()).thenReturn(properties);
        ChromaApi.Collection collection = new ChromaApi.Collection("collection-id", "housing-rag", Map.of());
        when(api.listCollections("SpringAiTenant", "SpringAiDatabase"))
                .thenReturn(List.of(collection));
        when(api.countEmbeddings("SpringAiTenant", "SpringAiDatabase", "collection-id"))
                .thenReturn(documentCount);
        if (marker != null) {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(marker));
        }
    }

    private Document marker(long houseCount,
                            long expectedDocuments,
                            LocalDateTime sourceUpdatedAt) {
        Map<String, Object> metadata = Map.of(
                "type", "index_state",
                "index_schema_version", KnowledgeIndexService.INDEX_SCHEMA_VERSION,
                "house_count", String.valueOf(houseCount),
                "expected_document_count", String.valueOf(expectedDocuments),
                "source_updated_at", KnowledgeIndexService.watermark(sourceUpdatedAt),
                "synced_at", "2026-07-27T04:31:00Z");
        return new Document("RAG index synchronization state", metadata);
    }

    private RagVectorHealthIndicator indicator() {
        return new RagVectorHealthIndicator(vectorStores, chromaApis, chromaProperties, houses);
    }
}
