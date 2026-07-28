package com.bulongyu.housing.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统健康检查接口控制器
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    /**
     * 返回后端服务健康状态。
     */
    @GetMapping
    Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
