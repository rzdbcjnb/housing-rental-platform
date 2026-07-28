package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 聊天室查询结果
 */
public record RoomRow(
        Long id, String roomType, String name, Long houseId, String houseTitle, String houseImage,
        Long otherProfileId, Long otherUserId, String otherUsername, String otherAvatar,
        Boolean otherOnline, Long lastMessageId, String lastContent, String lastMessageType,
        String lastSenderName, LocalDateTime lastMessageAt, Long unreadCount,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
