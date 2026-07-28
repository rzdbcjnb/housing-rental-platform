package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 通知消息返回数据
 */
public record NotificationMessageView(Long id, String messageType, String title, String content,
                          boolean isRead, LocalDateTime createTime, String senderName,
                          Long relatedHouse, String relatedHouseTitle) {
}
