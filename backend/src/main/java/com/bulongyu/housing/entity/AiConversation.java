package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * AI 客服会话
 */
public record AiConversation(Long id, Long userId, String title,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
}
