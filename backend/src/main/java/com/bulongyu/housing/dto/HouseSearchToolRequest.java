package com.bulongyu.housing.dto;

import java.math.BigDecimal;

/**
 * AI 房源搜索工具请求参数。
 *
 * @param query 用户的原始检索需求
 * @param region 城市或区域名称
 * @param minimumPrice 最低月租
 * @param maximumPrice 最高月租
 * @param targetPrice 仅用于排序的期望月租
 * @param bedroomCount 精确卧室数量
 * @param minimumBedroomCount 最少卧室数量
 * @param minimumLivingRoomCount 最少客厅数量
 * @param minimumBathroomCount 最少卫生间数量
 * @param minimumKitchenCount 最少厨房数量
 * @param limit 返回数量上限
 */
public record HouseSearchToolRequest(
        String query,
        String region,
        BigDecimal minimumPrice,
        BigDecimal maximumPrice,
        BigDecimal targetPrice,
        Integer bedroomCount,
        Integer minimumBedroomCount,
        Integer minimumLivingRoomCount,
        Integer minimumBathroomCount,
        Integer minimumKitchenCount,
        Integer limit) {
}
