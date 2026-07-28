package com.bulongyu.housing.dto;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 通知批量删除请求参数
 */
public record NotificationBatchDeleteRequest(List<Long> ids) {
}
