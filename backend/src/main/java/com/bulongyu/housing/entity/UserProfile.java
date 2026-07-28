package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 用户用户资料
 */
public record UserProfile(
        Long id,
        Long userId,
        String phone,
        String role,
        String avatar,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
