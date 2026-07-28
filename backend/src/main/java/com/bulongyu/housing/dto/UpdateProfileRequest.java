package com.bulongyu.housing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 用户资料更新请求参数
 */
public record UpdateProfileRequest(
        @Size(min = 3, max = 150, message = "用户名长度必须为 3 到 150 个字符") String username,
        @Size(max = 20, message = "手机号长度不能超过 20 个字符") String phone,
        @Size(max = 500, message = "头像地址长度不能超过 500 个字符") String avatar
) {
}
