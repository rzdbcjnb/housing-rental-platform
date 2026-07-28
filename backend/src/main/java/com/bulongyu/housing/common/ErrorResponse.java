package com.bulongyu.housing.common;

import java.time.Instant;

/**
 * 统一错误响应
 */
public record ErrorResponse(String detail, String code, String requestId, Instant timestamp) {
}
