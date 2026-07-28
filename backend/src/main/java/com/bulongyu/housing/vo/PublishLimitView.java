package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.util.List;
/**
 * 发布额度返回数据
 */
public record PublishLimitView(boolean needPay, long freeRemaining, long totalPublished) {
}
