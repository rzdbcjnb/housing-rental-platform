package com.bulongyu.housing.vo;

import java.util.List;

/**
 * AI 客服聊天响应。
 *
 * @param response AI 最终回复
 * @param type 响应内容类型
 * @param houses 房源卡片列表
 * @param sources 知识来源列表
 * @param pendingActions 等待用户确认的操作列表
 * @param retrievalStatus 房源检索状态，非房源回答为空
 * @param conversationId AI 会话编号
 * @param requestId 请求追踪编号
 */
public record AiChatResponse(
        String response,
        String type,
        List<AiHouseView> houses,
        List<AiSourceView> sources,
        List<AiPendingActionView> pendingActions,
        String retrievalStatus,
        Long conversationId,
        String requestId) {
}