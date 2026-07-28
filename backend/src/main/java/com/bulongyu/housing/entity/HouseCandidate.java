package com.bulongyu.housing.entity;

import java.math.BigDecimal;

/**
 * 房源候选
 */
public record HouseCandidate(Long id, String title, String description, BigDecimal price,
                             Integer area, String rooms, Integer bedroomCount,
                             Integer livingRoomCount, Integer bathroomCount,
                             Integer kitchenCount, String regionName, String districtName,
                             String cityName, String image) {
    /**
     * 房源候选
     */
    public String fullRegionName() {
        return java.util.stream.Stream.of(cityName, districtName, regionName)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining("-"));
    }
}
