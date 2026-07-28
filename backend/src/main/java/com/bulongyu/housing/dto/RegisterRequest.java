package com.bulongyu.housing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 注册请求参数
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 150, message = "用户名长度必须为 3 到 150 个字符") String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度必须为 6 到 128 个字符") String password,
        @NotBlank(message = "手机号不能为空")
        @Size(max = 20, message = "手机号长度不能超过 20 个字符") String phone,
        @Pattern(regexp = "tenant|landlord", message = "角色必须为 tenant 或 landlord") String role
) {
    /**
     * 注册请求参数
     *
     * @param username 用户名
     * @param password 用户密码
     * @param phone 手机号
     * @param role 角色
     */
    public RegisterRequest {
        role = role == null || role.isBlank() ? "tenant" : role;
    }
}
