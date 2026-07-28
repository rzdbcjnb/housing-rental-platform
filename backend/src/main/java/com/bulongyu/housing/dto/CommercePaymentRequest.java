package com.bulongyu.housing.dto;

import java.math.BigDecimal;
import java.util.List;
/**
 * 积分支付支付请求参数
 */
public record CommercePaymentRequest(BigDecimal amount) {
}
