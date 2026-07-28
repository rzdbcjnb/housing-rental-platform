package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.HouseCandidate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
/**
 * AI 客服房源返回数据
 */
public record AiHouseView(Long id, String title, BigDecimal price, Integer area, String rooms,
                        Integer bedroomCount, Integer livingRoomCount, Integer bathroomCount,
                        Integer kitchenCount, String image, String regionName) {
    /**
     * AI 客服房源返回数据
     *
     * @param house 候选房源
     */
public static AiHouseView from(HouseCandidate house) {
        return new AiHouseView(house.id(), house.title(), house.price(), house.area(), house.rooms(),
                house.bedroomCount(), house.livingRoomCount(), house.bathroomCount(),
                house.kitchenCount(), house.image(), house.fullRegionName());
    }
}
