package com.bulongyu.housing.dto;

import com.bulongyu.housing.entity.HouseRow;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 房源Upsert请求参数
 */
public record HouseUpsertRequest(
        @Size(min = 2, max = 200) String title,
        String description,
        @DecimalMin("1.00") BigDecimal price,
        @Min(1) Integer area,
        String rooms,
        @Min(0) Integer bedroomCount,
        @Min(0) Integer livingRoomCount,
        @Min(1) Integer bathroomCount,
        @Min(0) Integer kitchenCount,
        String houseType,
        Long region,
        String addressDetail,
        String image
) {
}
