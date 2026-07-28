package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.util.List;
/**
 * 推荐状态条目返回数据
 */
public record RecommendStatusItem(Long houseId, String title, String status, Integer points,
                                  double weight, Integer maxPoints, Integer clickCount) {
}
