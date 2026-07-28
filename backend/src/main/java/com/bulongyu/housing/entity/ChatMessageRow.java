package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 聊天消息查询结果
 */
public record ChatMessageRow(Long id, Long roomId, Long senderId, Long senderUserId,
                         String senderName, String senderAvatar, String messageType,
                         String content, Boolean read, LocalDateTime createdAt) {
}
