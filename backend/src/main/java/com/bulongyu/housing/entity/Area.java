package com.bulongyu.housing.entity;

/**
 * 地区
 */
public record Area(Long id, String name, Long parentId, Integer level, Boolean active) {
}
