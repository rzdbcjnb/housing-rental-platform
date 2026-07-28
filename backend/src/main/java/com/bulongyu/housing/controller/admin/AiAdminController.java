package com.bulongyu.housing.controller.admin;

import com.bulongyu.housing.security.CurrentUserId;


import com.bulongyu.housing.service.ai.KnowledgeIndexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 客服接口控制器
 */
@RestController
@RequestMapping("/api/admin/ai")
public class AiAdminController {
    private final KnowledgeIndexService index;

    /**
     * 初始化 {@code AiAdminController} 并注入所需依赖。
     *
     * @param index 知识索引服务
     */
    public AiAdminController(KnowledgeIndexService index) { this.index = index; }

    /**
     * 触发房源与租赁 FAQ 的全量向量索引同步。
     *
     * @param currentUserId 当前登录用户编号
     */
    @PostMapping("/index/sync/")
    public KnowledgeIndexService.IndexResult sync(@CurrentUserId Long currentUserId) {
        return index.syncAll(currentUserId);
    }
}
