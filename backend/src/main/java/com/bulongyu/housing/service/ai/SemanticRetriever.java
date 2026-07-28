package com.bulongyu.housing.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 房源语义向量召回服务。
 */
@Component
public class SemanticRetriever {
    private static final Logger log = LoggerFactory.getLogger(SemanticRetriever.class);

    private final ObjectProvider<VectorStore> stores;
    private final double similarityThreshold;

    /**
     * 创建房源语义向量召回服务。
     *
     * @param stores 可选的向量存储提供器
     * @param similarityThreshold 语义召回最低相似度
     */
    public SemanticRetriever(
            ObjectProvider<VectorStore> stores,
            @Value("${app.ai.retrieval.similarity-threshold:0.58}") double similarityThreshold) {
        if (similarityThreshold < 0 || similarityThreshold > 1) {
            throw new IllegalArgumentException("similarityThreshold must be between 0 and 1");
        }
        this.stores = stores;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * 从向量库召回房源编号，并显式区分成功命中、成功零命中和基础设施不可用。
     *
     * @param query 用户输入的问题
     * @param limit 返回数量上限
     * @return 语义召回结果和向量库状态
     */
    public Retrieval retrieveHouseIds(String query, int limit) {
        try {
            VectorStore store = stores.getIfAvailable();
            if (store == null) {
                return inactive();
            }
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(limit * 4)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression("type == 'house'")
                    .build();
            List<Document> documents = store.similaritySearch(searchRequest);
            LinkedHashMap<Long, Double> scores = new LinkedHashMap<>();
            for (Document document : documents) {
                Object type = document.getMetadata().get("type");
                Object rawId = document.getMetadata().get("house_id");
                // 向量元数据属于外部输入，校验类型和编号后才能进入候选集合。
                if (!"house".equals(type) || rawId == null) {
                    continue;
                }
                try {
                    long id = Long.parseLong(rawId.toString());
                    scores.putIfAbsent(id, document.getScore() == null ? 0.0 : document.getScore());
                    if (scores.size() == limit) {
                        break;
                    }
                }
                catch (NumberFormatException ignored) {
                    // 忽略格式非法的向量元数据，不影响其他候选。
                }
            }
            RetrievalStatus status = scores.isEmpty()
                    ? RetrievalStatus.SUCCESS_EMPTY
                    : RetrievalStatus.SUCCESS_WITH_RESULTS;
            return new Retrieval(status, new ArrayList<>(scores.keySet()), Map.copyOf(scores));
        }
        catch (RuntimeException exception) {
            log.warn("房源向量检索不可用，交由调用方执行安全降级，参数：exceptionType={}",
                    exception.getClass().getSimpleName());
            return inactive();
        }
    }

    /**
     * 创建向量检索未启用的稳定结果。
     */
    private Retrieval inactive() {
        return new Retrieval(RetrievalStatus.UNAVAILABLE, List.of(), Map.of());
    }

    /**
     * 向量召回运行状态。
     */
    public enum RetrievalStatus {
        SUCCESS_WITH_RESULTS,
        SUCCESS_EMPTY,
        UNAVAILABLE
    }

    /**
     * 房源语义召回结果。
     */
    public record Retrieval(RetrievalStatus status, List<Long> ids, Map<Long, Double> scores) {
        public Retrieval {
            ids = List.copyOf(ids);
            scores = Map.copyOf(scores);
        }
    }
}