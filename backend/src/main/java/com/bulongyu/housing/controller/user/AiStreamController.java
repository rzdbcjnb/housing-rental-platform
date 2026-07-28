package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.dto.AiChatRequest;
import com.bulongyu.housing.filter.RequestIdFilter;
import com.bulongyu.housing.security.CurrentUserId;
import com.bulongyu.housing.service.ai.AiStreamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 客服 SSE 流式接口控制器。
 */
@RestController
@RequestMapping("/api/ai/chat")
public class AiStreamController {
    private final AiStreamService streamService;

    /**
     * 初始化 AI SSE 流式控制器。
     *
     * @param streamService AI SSE 流式服务
     */
    public AiStreamController(AiStreamService streamService) {
        this.streamService = streamService;
    }

    /**
     * 以标准 SSE 事件协议处理一次 AI 客服对话。
     *
     * @param currentUserId 当前认证用户编号
     * @param request AI 对话请求
     * @param httpRequest HTTP 请求对象
     * @return SSE 连接
     */
    @PostMapping(value = "/stream/", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {
        Object requestId = httpRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return streamService.stream(
                currentUserId,
                request,
                requestId == null ? "" : requestId.toString());
    }
}
