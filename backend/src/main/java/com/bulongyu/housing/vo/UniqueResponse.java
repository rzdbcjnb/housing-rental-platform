package com.bulongyu.housing.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 唯一性检查响应
 */
public record UniqueResponse(String field, String value, boolean exists, String message) {
}
