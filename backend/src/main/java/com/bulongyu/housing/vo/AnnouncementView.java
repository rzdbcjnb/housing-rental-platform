package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 公告返回数据
 */
public record AnnouncementView(Long id, String title, String content, String authorName,
                               boolean isActive, LocalDateTime createTime,
                               LocalDateTime updateTime) {
}
