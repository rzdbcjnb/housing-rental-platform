package com.bulongyu.housing.vo;

import java.math.BigDecimal;
import java.util.List;
/**
 * 账户返回数据
 */
public record AccountView(Integer balance, Integer totalPurchased, Integer totalInvested) {
}
