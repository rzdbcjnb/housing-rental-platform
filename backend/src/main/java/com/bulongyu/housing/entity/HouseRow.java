package com.bulongyu.housing.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 房源查询结果
 */
public record HouseRow(
        Long id, String title, String description, BigDecimal price, Integer area, String rooms,
        Integer bedroomCount, Integer livingRoomCount, Integer bathroomCount, Integer kitchenCount,
        String houseType, Long regionId, String addressDetail, String image, Long landlordId,
        String status, Integer clickCount, Boolean active, LocalDateTime createTime,
        LocalDateTime updateTime, String regionName, String districtName, String cityName,
        Long landlordUserId, String landlordUsername, String landlordPhone, String landlordAvatar
) {
    /**
     * 房源查询结果
     */
    public String fullRegionName() {
        return java.util.stream.Stream.of(cityName, districtName, regionName)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining("-"));
    }
}
