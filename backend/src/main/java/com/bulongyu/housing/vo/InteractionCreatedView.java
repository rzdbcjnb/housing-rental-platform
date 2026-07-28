package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 收藏与浏览历史创建结果返回数据
 */
public record InteractionCreatedView(Long id, Long house, LocalDateTime createTime) {
}
