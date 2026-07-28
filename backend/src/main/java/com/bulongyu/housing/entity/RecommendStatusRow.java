package com.bulongyu.housing.entity;

/**
 * 推荐状态查询结果
 */
public record RecommendStatusRow(Long houseId, String title, String status, Integer points,
                                 Integer clickCount) {
}
