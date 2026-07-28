package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.HouseRow;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 房源列表返回数据
 */
public record HouseListView(
        Long id, String title, BigDecimal price, Integer area, String rooms,
        Integer bedroomCount, Integer livingRoomCount, Integer bathroomCount,
        Integer kitchenCount, String image, String regionName, String status
) {
    /**
     * 房源列表返回数据
     *
     * @param row 数据库查询结果
     */
public static HouseListView from(HouseRow row) {
        return new HouseListView(row.id(), row.title(), row.price(), row.area(), row.rooms(),
                row.bedroomCount(), row.livingRoomCount(), row.bathroomCount(),
                row.kitchenCount(), row.image(), row.fullRegionName(), row.status());
    }
}
