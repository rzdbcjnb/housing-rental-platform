package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.dto.AiActionConfirmRequest;
import com.bulongyu.housing.security.CurrentUserId;
import com.bulongyu.housing.service.ai.AiActionService;
import com.bulongyu.housing.vo.AiActionResultView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 待确认写操作接口控制器。
 */
@RestController
@RequestMapping("/api/ai/actions")
public class AiActionController {
    private final AiActionService actionService;

    /**
     * 初始化 AI 待确认操作控制器。
     *
     * @param actionService AI 待确认操作服务
     */
    public AiActionController(AiActionService actionService) {
        this.actionService = actionService;
    }

    /**
     * 执行用户已经明确确认的一次性操作令牌。
     *
     * @param currentUserId 当前认证用户编号
     * @param token 一次性确认令牌
     * @param request 确认请求
     * @return 操作执行结果
     */
    @PostMapping("/{token}/confirm/")
    public AiActionResultView confirm(
            @CurrentUserId Long currentUserId,
            @PathVariable String token,
            @Valid @RequestBody AiActionConfirmRequest request) {
        return actionService.confirm(currentUserId, request.conversationId(), token);
    }
}
