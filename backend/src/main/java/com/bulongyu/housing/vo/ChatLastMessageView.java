package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 聊天最后一条消息返回数据
 */
public record ChatLastMessageView(Long id, String content, String messageType,
                              String senderName, LocalDateTime createdAt) {
}
