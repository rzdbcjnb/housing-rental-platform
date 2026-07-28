package com.bulongyu.housing.dto;

import java.math.BigDecimal;
import java.util.List;
/**
 * 推荐积分请求参数
 */
public record RecommendPointsRequest(Long houseId, Integer points) {
}
