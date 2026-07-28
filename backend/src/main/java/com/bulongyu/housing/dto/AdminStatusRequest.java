package com.bulongyu.housing.dto;

import com.bulongyu.housing.entity.AdminHouseRow;
import com.bulongyu.housing.entity.AdminUserRow;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 后台管理状态请求参数
 */
public record AdminStatusRequest(Boolean isActive) {}
