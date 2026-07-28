package com.bulongyu.housing.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 认证Payload返回数据
 */
public record AuthPayload(AuthUserView user, TokenPair tokens) {
}
