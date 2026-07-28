package com.bulongyu.housing.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 认证响应
 */
public record AuthResponse(String message, AuthPayload data) {
}
