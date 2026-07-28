package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.HouseCandidate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
/**
 * AI 客服会话返回数据
 */
public record AiConversationView(Long id, String title, LocalDateTime createdAt,
                               LocalDateTime updatedAt) {}
