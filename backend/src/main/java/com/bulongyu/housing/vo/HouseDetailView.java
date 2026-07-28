package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.HouseRow;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 房源详情返回数据
 */
public record HouseDetailView(
        Long id, String title, String description, BigDecimal price, Integer area, String rooms,
        Integer bedroomCount, Integer livingRoomCount, Integer bathroomCount,
        Integer kitchenCount, String houseType, String houseTypeDisplay, Long region,
        String regionName, String addressDetail, String image, Long landlord,
        LandlordView landlordInfo, String status, String statusDisplay, Boolean isActive,
        LocalDateTime createTime, LocalDateTime updateTime
) {
    /**
     * 房源详情返回数据
     *
     * @param row 数据库查询结果
     */
    public static HouseDetailView from(HouseRow row) {
        return from(row, true);
    }

    public static HouseDetailView from(HouseRow row, boolean includePrivate) {
        return new HouseDetailView(row.id(), row.title(), row.description(), row.price(), row.area(),
    /**
     * 按访问权限生成房源详情，公开访问时隐藏精确地址和手机号。
     *
     * @param row 数据库查询结果
     * @param includePrivate 是否返回隐私字段
     * @return 房源详情
     */
                row.rooms(), row.bedroomCount(), row.livingRoomCount(), row.bathroomCount(),
                row.kitchenCount(), row.houseType(), typeDisplay(row.houseType()), row.regionId(),
                row.fullRegionName(), includePrivate ? row.addressDetail() : "", row.image(), row.landlordId(),
                new LandlordView(row.landlordUserId(), row.landlordId(), row.landlordUsername(),
                        includePrivate ? row.landlordPhone() : null, row.landlordAvatar()),
                row.status(), statusDisplay(row.status()), row.active(),
                row.createTime(), row.updateTime());
    }

    private static String typeDisplay(String value) {
        return "whole".equals(value) ? "\u6574\u79df" : "share".equals(value) ? "\u5408\u79df" : value;
    }

    private static String statusDisplay(String value) {
        return switch (value == null ? "" : value) {
            case "pending" -> "\u5f85\u5ba1\u6838";
            case "approved" -> "\u5df2\u901a\u8fc7";
            case "rejected" -> "\u5df2\u62d2\u7edd";
            case "offline" -> "\u5df2\u4e0b\u67b6";
            default -> value;
        };
    }}
