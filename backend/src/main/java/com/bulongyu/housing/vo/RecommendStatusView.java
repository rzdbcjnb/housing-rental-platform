package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.util.List;
/**
 * 推荐状态返回数据
 */
public record RecommendStatusView(List<RecommendStatusItem> houses) {
}
