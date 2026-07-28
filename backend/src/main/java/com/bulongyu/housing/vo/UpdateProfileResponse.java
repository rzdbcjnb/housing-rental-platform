package com.bulongyu.housing.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 用户资料更新响应
 */
public record UpdateProfileResponse(String message, AuthUserView data) {
}
