package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.HouseRow;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 房源详情响应
 */
public record HouseDetailResponse(String detail) {
}
