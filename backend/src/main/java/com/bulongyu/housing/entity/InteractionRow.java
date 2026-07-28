package com.bulongyu.housing.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 收藏与浏览历史查询结果
 */
public record InteractionRow(
        Long id, LocalDateTime createTime, Long houseId, String title, BigDecimal price,
        Integer area, String rooms, String image, String regionName, String districtName,
        String cityName
) {
    /**
     * 收藏与浏览历史查询结果
     */
    public String fullRegionName() {
        return Stream.of(cityName, districtName, regionName)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("-"));
    }
}
