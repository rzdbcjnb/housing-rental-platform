package com.bulongyu.housing.vo;

import java.time.LocalDateTime;

/**
 * AI 待确认操作执行结果。
 *
 * @param action 已执行操作类型
 * @param favoriteId 新增收藏编号
 * @param roomId 聊天室编号
 * @param textMessageId 文本消息编号
 * @param houseShareMessageId 房源卡片消息编号
 * @param executedAt 执行完成时间
 */
public record AiActionResultView(
        String action,
        Long favoriteId,
        Long roomId,
        Long textMessageId,
        Long houseShareMessageId,
        LocalDateTime executedAt) {
}
