package com.bulongyu.housing.dto;

import jakarta.validation.constraints.NotNull;

/**
 * AI 待确认操作执行请求。
 *
 * @param conversationId 待确认操作所属的 AI 会话编号
 */
public record AiActionConfirmRequest(
        @NotNull(message = "会话编号不能为空") Long conversationId) {
}
