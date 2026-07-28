package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 收藏状态返回数据
 */
public record FavoriteStatus(boolean isFavorited, Long favoriteId) {
}
