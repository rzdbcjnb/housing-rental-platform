package com.bulongyu.housing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 令牌刷新请求参数
 */
public record RefreshRequest(@NotBlank(message = "刷新令牌不能为空") String refresh) {
}
