package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 公告查询结果
 */
public record AnnouncementRow(Long id, String title, String content, String authorName,
                              Boolean active, LocalDateTime createTime, LocalDateTime updateTime) {
}
