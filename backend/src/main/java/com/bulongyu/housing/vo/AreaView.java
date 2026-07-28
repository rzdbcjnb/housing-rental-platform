package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.Area;

/**
 * 地区返回数据
 */
public record AreaView(Long id, String name, Long parent, Integer level) {
/**
 * 地区返回数据
 *
 * @param area 地区
 */
public static AreaView from(Area area) {
    return new AreaView(area.id(), area.name(), area.parentId(), area.level());
}
}
