package com.bulongyu.housing.entity;

import java.math.BigDecimal;

/**
 * 房源Query
 */
public record HouseQuery(
        String keyword, String city, String district, String street,
        BigDecimal priceMin, BigDecimal priceMax, String rooms,
        Integer areaMin, Integer areaMax, String houseType,
        Integer bedroomMin, Integer livingRoomMin, Integer bathroomMin, Integer kitchenMin
) {
    /**
     * 房源Query
     */
    public String normalizedKeyword() { return normalize(keyword); }
    /**
     * 房源Query
     */
    public String normalizedCity() { return normalize(city); }
    /**
     * 房源Query
     */
    public String normalizedDistrict() { return normalize(district); }
    /**
     * 房源Query
     */
    public String normalizedStreet() { return normalize(street); }
    /**
     * 房源Query
     */
    public String normalizedRooms() { return normalize(rooms); }
    /**
     * 房源Query
     */
    public String normalizedHouseType() { return normalize(houseType); }

    /**
     * 房源Query
     *
     * @param value 字段值
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
