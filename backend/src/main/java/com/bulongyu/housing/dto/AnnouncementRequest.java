package com.bulongyu.housing.dto;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 公告请求参数
 */
public record AnnouncementRequest(String title, String content, Boolean isActive) {
}
