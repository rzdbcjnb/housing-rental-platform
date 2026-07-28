package com.bulongyu.housing.vo;

import java.math.BigDecimal;

/**
 * AI 待确认操作展示使用的房源卡片。
 *
 * @param id 房源编号
 * @param title 房源标题
 * @param price 月租价格
 * @param rooms 户型文本
 * @param image 封面图片
 * @param regionName 所在区域
 */
public record AiActionHouseView(
        Long id,
        String title,
        BigDecimal price,
        String rooms,
        String image,
        String regionName) {
}
