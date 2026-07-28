package com.bulongyu.housing.vo;

import java.time.LocalDateTime;

/**
 * 需要用户明确确认后才能执行的 AI 操作预览。
 *
 * @param token 一次性确认令牌
 * @param action 白名单操作类型
 * @param conversationId 所属 AI 会话编号
 * @param house 房源卡片
 * @param content 最终将发送的确切文本
 * @param expiresAt 令牌过期时间
 */
public record AiPendingActionView(
        String token,
        String action,
        Long conversationId,
        AiActionHouseView house,
        String content,
        LocalDateTime expiresAt) {
}
