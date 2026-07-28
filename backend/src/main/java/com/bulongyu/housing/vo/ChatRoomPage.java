package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 聊天聊天室分页结果
 */
public record ChatRoomPage(long count, List<ChatRoomView> results, int page, int pageSize) {
}
