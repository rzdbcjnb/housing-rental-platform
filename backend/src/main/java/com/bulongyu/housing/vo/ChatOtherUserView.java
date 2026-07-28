package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 聊天对方用户返回数据
 */
public record ChatOtherUserView(Long id, String username, String avatar, boolean isOnline) {
}
