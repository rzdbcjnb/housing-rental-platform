package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.HouseQuery;
import com.bulongyu.housing.mapper.HouseMapper;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 验证 RAG 向量链路、Collection 内容和源数据同步水位。
 */
@Component("ragVectorHealthIndicator")
public class RagVectorHealthIndicator implements HealthIndicator {
    private static final HouseQuery ALL = new HouseQuery(null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

    private final ObjectProvider<VectorStore> vectorStores;
    private final ObjectProvider<ChromaApi> chromaApis;
    private final ObjectProvider<ChromaVectorStoreProperties> chromaProperties;
    private final HouseMapper houses;

    public RagVectorHealthIndicator(ObjectProvider<VectorStore> vectorStores,
                                    ObjectProvider<ChromaApi> chromaApis,
                                    ObjectProvider<ChromaVectorStoreProperties> chromaProperties,
                                    HouseMapper houses) {
        this.vectorStores = vectorStores;
        this.chromaApis = chromaApis;
        this.chromaProperties = chromaProperties;
        this.houses = houses;
    }

    @Override
    public Health health() {
        try {
            VectorStore store = vectorStores.getIfAvailable();
            if (store == null) {
                return unavailable("VECTOR_STORE_UNAVAILABLE");
            }
            ChromaApi api = chromaApis.getIfAvailable();
            ChromaVectorStoreProperties properties = chromaProperties.getIfAvailable();
            if (api == null || properties == null) {
                return unavailable("CHROMA_CLIENT_UNAVAILABLE");
            }

            List<ChromaApi.Collection> collections = api.listCollections(
                    properties.getTenantName(),
                    properties.getDatabaseName());
            ChromaApi.Collection collection = collections == null
                    ? null
                    : collections.stream()
                            .filter(candidate -> properties.getCollectionName().equals(candidate.name()))
                            .findFirst()
                            .orElse(null);
            if (collection == null) {
                return unavailable("COLLECTION_MISSING");
            }

            long actualDocuments = valueOrZero(api.countEmbeddings(
                    properties.getTenantName(),
                    properties.getDatabaseName(),
                    collection.id()));
            if (actualDocuments == 0) {
                return unavailable("COLLECTION_EMPTY");
            }

            IndexMarker marker = marker(store);
            if (marker == null) {
                return Health.outOfService()
                        .withDetail("reason", "INDEX_MARKER_MISSING")
                        .withDetail("document_count", actualDocuments)
                        .build();
            }

            long sourceHouseCount = houses.countPublic(ALL);
            LocalDateTime sourceUpdatedAt = houses.latestPublicUpdateTime();
            long expectedDocuments = KnowledgeIndexService.expectedDocumentCount(sourceHouseCount);
            if (actualDocuments != expectedDocuments
                    || marker.houseCount() != sourceHouseCount
                    || marker.expectedDocumentCount() != expectedDocuments
                    || !sameWatermark(marker.sourceUpdatedAt(), sourceUpdatedAt)
                    || !KnowledgeIndexService.INDEX_SCHEMA_VERSION.equals(marker.schemaVersion())) {
                return Health.outOfService()
                        .withDetail("reason", "INDEX_STALE")
                        .withDetail("document_count", actualDocuments)
                        .withDetail("expected_document_count", expectedDocuments)
                        .withDetail("source_house_count", sourceHouseCount)
                        .withDetail("indexed_house_count", marker.houseCount())
                        .withDetail("index_schema_version", marker.schemaVersion())
                        .build();
            }

            return Health.up()
                    .withDetail("document_count", actualDocuments)
                    .withDetail("source_house_count", sourceHouseCount)
                    .withDetail("synchronization", "CURRENT")
                    .withDetail("index_schema_version", marker.schemaVersion())
                    .withDetail("last_successful_sync", marker.syncedAt())
                    .build();
        }
        catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("reason", "VECTOR_HEALTH_CHECK_FAILED")
                    .withDetail("exception", exception.getClass().getSimpleName())
                    .build();
        }
    }

    private IndexMarker marker(VectorStore store) {
        SearchRequest request = SearchRequest.builder()
                .query("RAG index synchronization state")
                .topK(2)
                .similarityThreshold(0)
                .filterExpression("type == 'index_state'")
                .build();
        List<Document> documents = store.similaritySearch(request);
        if (documents == null || documents.size() != 1) {
            return null;
        }
        Map<String, Object> metadata = documents.get(0).getMetadata();
        try {
            return new IndexMarker(
                    text(metadata, "index_schema_version"),
                    Long.parseLong(text(metadata, "house_count")),
                    Long.parseLong(text(metadata, "expected_document_count")),
                    text(metadata, "source_updated_at"),
                    text(metadata, "synced_at"));
        }
        catch (RuntimeException exception) {
            return null;
        }
    }

    private String text(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing index marker field");
        }
        return value.toString();
    }

    private boolean sameWatermark(String indexedValue, LocalDateTime sourceValue) {
        return KnowledgeIndexService.watermark(sourceValue).equals(indexedValue);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private Health unavailable(String reason) {
        return Health.outOfService().withDetail("reason", reason).build();
    }

    private record IndexMarker(String schemaVersion,
                               long houseCount,
                               long expectedDocumentCount,
                               String sourceUpdatedAt,
                               String syncedAt) {
    }
}
