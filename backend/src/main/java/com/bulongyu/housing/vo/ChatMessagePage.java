package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 聊天消息分页结果
 */
public record ChatMessagePage(long count, List<ChatMessageView> results, int page, int pageSize) {
}
