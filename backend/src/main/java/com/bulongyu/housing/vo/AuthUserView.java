package com.bulongyu.housing.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 认证用户返回数据
 */
public record AuthUserView(Long id, String username, String phone, String role, String avatar) {
}
