package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.util.List;
/**
 * 积分响应
 */
public record PointResponse(String message, Integer points, double weight,
                            BigDecimal amount, Integer balance, Integer housePoints) {
}
