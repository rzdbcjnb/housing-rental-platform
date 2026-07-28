package com.bulongyu.housing.dto;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 聊天创建聊天室请求参数
 */
public record ChatCreateRoomRequest(Long userId, Long houseId) {
}
