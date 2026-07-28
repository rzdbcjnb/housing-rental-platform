package com.bulongyu.housing.controller;

import com.bulongyu.housing.vo.AreaView;

import com.bulongyu.housing.mapper.AreaMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地区接口控制器
 */
@RestController
@RequestMapping("/api/areas")
public class AreaController {
    private final AreaMapper areaMapper;

    /**
     * 初始化 {@code AreaController} 并注入所需依赖。
     *
     * @param areaMapper 地区Mapper
     */
    public AreaController(AreaMapper areaMapper) {
        this.areaMapper = areaMapper;
    }

    /**
     * 根据筛选条件分页查询已发布房源。
     *
     * @param level 地区层级
     * @param parentId 上级地区编号
     * @return 房源分页结果
     */
    @GetMapping("/")
    List<AreaView> list(@RequestParam(required = false) Integer level,
                        @RequestParam(name = "parent_id", required = false) Long parentId) {
        return areaMapper.findActive(level, parentId).stream().map(AreaView::from).toList();
    }
}
