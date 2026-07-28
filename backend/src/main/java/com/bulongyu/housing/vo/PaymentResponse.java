package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.util.List;
/**
 * 支付响应
 */
public record PaymentResponse(String message, Long recordId, BigDecimal amount) {
}
