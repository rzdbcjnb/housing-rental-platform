package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 通知消息查询结果
 */
public record NotificationMessageRow(Long id, String messageType, String title, String content, Boolean read,
                         LocalDateTime createTime, String senderName, Long relatedHouseId,
                         String relatedHouseTitle) {
}
