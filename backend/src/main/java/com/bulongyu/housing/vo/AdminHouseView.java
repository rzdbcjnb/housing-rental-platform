package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.AdminHouseRow;
import com.bulongyu.housing.entity.AdminUserRow;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 后台管理房源返回数据
 */
public record AdminHouseView(Long id, String title, String description, BigDecimal price, Integer area,
                        String rooms, String houseType, Long region, String regionName,
                        String addressDetail, String image, Long landlord, String landlordUsername,
                        String landlordPhone, String status, Boolean isActive,
                        LocalDateTime createTime, LocalDateTime updateTime) {
    /**
     * 后台管理房源返回数据
     *
     * @param row 数据库查询结果
     */
public static AdminHouseView from(AdminHouseRow row) {
        return new AdminHouseView(row.id(), row.title(), row.description(), row.price(), row.area(), row.rooms(),
                row.houseType(), row.region(), row.regionName(), row.addressDetail(), row.image(),
                row.landlord(), row.landlordUsername(), row.landlordPhone(), row.status(), row.active(),
                row.createTime(), row.updateTime());
    }
}
