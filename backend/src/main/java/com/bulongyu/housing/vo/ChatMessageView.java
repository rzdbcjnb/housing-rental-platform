package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 聊天消息返回数据
 */
public record ChatMessageView(Long id, Long room, Long sender, Long senderUserId,
                          String senderName, String senderAvatar, String messageType,
                          Object content, boolean isRead, LocalDateTime createdAt) {
}
