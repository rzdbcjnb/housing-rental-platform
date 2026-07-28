package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * AI 客服消息
 */
public record AiMessage(Long id, Long conversationId, String role, String content,
                        String metadata, LocalDateTime createdAt) {
}
