package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 认证用户
 */
public record AuthUser(
        Long id,
        String username,
        String password,
        boolean active,
        boolean staff,
        boolean superuser,
        LocalDateTime lastLogin,
        LocalDateTime dateJoined
) {
}
