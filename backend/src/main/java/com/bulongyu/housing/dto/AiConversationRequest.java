package com.bulongyu.housing.dto;

import com.bulongyu.housing.entity.HouseCandidate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
/**
 * AI 客服会话请求参数
 */
public record AiConversationRequest(@Size(max = 200) String title) {}
