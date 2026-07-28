package com.bulongyu.housing.entity;

import java.util.List;

/**
 * AI 会话中由服务端维护的结构化业务状态。
 *
 * @param currentHouseId 当前正在讨论的房源编号
 * @param candidateHouseIds 最近一次推荐返回的候选房源编号
 * @param searchConstraints 最近一次有效的结构化找房条件
 * @param lastIntent 最近一次明确的业务意图
 */
public record AiConversationContext(Long currentHouseId,
                                    List<Long> candidateHouseIds,
                                    List<SearchConstraint> searchConstraints,
                                    String lastIntent) {

    public AiConversationContext {
        candidateHouseIds = candidateHouseIds == null ? List.of() : List.copyOf(candidateHouseIds);
        searchConstraints = searchConstraints == null ? List.of() : List.copyOf(searchConstraints);
        lastIntent = lastIntent == null ? "" : lastIntent;
    }

    /**
     * 创建没有任何业务指向的新会话状态。
     */
    public static AiConversationContext empty() {
        return new AiConversationContext(null, List.of(), List.of(), "");
    }
}