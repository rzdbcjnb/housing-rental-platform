package com.bulongyu.housing.dto;

import com.bulongyu.housing.entity.AdminHouseRow;
import com.bulongyu.housing.entity.AdminUserRow;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 后台管理用户请求参数
 */
public record AdminUserRequest(@Size(max = 150) String username, String password,
                          @Size(max = 20) String phone, String role, Boolean isActive) {}
