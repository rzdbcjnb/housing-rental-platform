package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 收藏与浏览历史条目返回数据
 */
public record InteractionItemView(Long id, HouseSummary house, LocalDateTime createTime) {
}
