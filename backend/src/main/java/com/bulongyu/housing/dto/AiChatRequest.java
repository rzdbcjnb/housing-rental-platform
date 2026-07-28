package com.bulongyu.housing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * AI 客服聊天请求参数。
 *
 * @param message 用户消息
 * @param conversationId 已有会话编号
 * @param newConversation 是否创建新会话
 * @param houseId 用户当前选择的房源编号
 */
public record AiChatRequest(
        @NotBlank @Size(max = 2000) String message,
        Long conversationId,
        Boolean newConversation,
        @Positive Long houseId) {

    /**
     * 兼容不携带房源上下文的普通对话请求。
     */
    public AiChatRequest(String message, Long conversationId, Boolean newConversation) {
        this(message, conversationId, newConversation, null);
    }
}