package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 聊天房源分享返回数据
 */
public record ChatHouseShareView(Long id, String messageType, Object content) {
}
