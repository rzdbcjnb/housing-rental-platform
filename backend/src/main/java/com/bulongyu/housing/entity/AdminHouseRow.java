package com.bulongyu.housing.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台管理房源查询结果
 */
public record AdminHouseRow(Long id, String title, String description, BigDecimal price,
                            Integer area, String rooms, String houseType, Long region,
                            String regionName, String addressDetail, String image, Long landlord,
                            String landlordUsername, String landlordPhone, String status,
                            Boolean active, LocalDateTime createTime, LocalDateTime updateTime) {}
