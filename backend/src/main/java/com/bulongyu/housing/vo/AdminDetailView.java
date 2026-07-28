package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.AdminHouseRow;
import com.bulongyu.housing.entity.AdminUserRow;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 后台管理Detail返回数据
 */
public record AdminDetailView(String detail) {}
