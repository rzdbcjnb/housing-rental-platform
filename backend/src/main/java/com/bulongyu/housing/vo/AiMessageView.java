package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.HouseCandidate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
/**
 * AI 客服消息返回数据
 */
public record AiMessageView(Long id, String role, String content, Map<String, Object> metadata,
                          LocalDateTime createdAt) {}
