package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 后台管理用户查询结果
 */
public record AdminUserRow(Long id, String username, String email, Boolean active,
                           LocalDateTime dateJoined, String phone, String role, String avatar,
                           LocalDateTime profileCreateTime) {}
