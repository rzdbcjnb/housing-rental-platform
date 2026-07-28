package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 房源摘要返回数据
 */
public record HouseSummary(Long id, String title, BigDecimal price, Integer area,
                           String rooms, String image, String regionName) {
}
