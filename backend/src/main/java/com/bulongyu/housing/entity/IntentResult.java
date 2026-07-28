package com.bulongyu.housing.entity;

import java.util.List;

/**
 * 意图识别结果
 */
public record IntentResult(Intent intent, List<SearchConstraint> constraints,
                           Long houseId, String searchQuery, String clarification) {
    /**
     * 意图识别结果
     */
    public enum Intent { HOUSE_RECOMMEND, HOUSE_DETAIL, HOUSE_SIMILAR, KNOWLEDGE_QUERY, GENERAL_CHAT }

    /**
     * 意图识别结果
     *
     * @param intent 识别出的用户意图
     * @param constraints 检索约束集合
     * @param houseId 房源编号
     * @param searchQuery 用于检索的用户问题
     * @param clarification 需要向用户确认的问题
     */
    public IntentResult {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        searchQuery = searchQuery == null ? "" : searchQuery;
        clarification = clarification == null ? "" : clarification;
    }
}
