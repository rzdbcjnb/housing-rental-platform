package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.dto.AiChatRequest;
import com.bulongyu.housing.dto.AiConversationRequest;
import com.bulongyu.housing.filter.RequestIdFilter;
import com.bulongyu.housing.security.CurrentUserId;
import com.bulongyu.housing.service.ai.AiConversationService;
import com.bulongyu.housing.vo.AiChatResponse;
import com.bulongyu.housing.vo.AiConversationView;
import com.bulongyu.housing.vo.AiMessageView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 客服同步接口、会话和历史记录控制器。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiConversationService service;

    /**
     * 创建 AI 客服控制器。
     *
     * @param service AI 会话服务
     */
    public AiController(AiConversationService service) {
        this.service = service;
    }

    /**
     * 处理一次同步 AI 对话并保存用户消息与助手回复。
     *
     * @param currentUserId 当前登录用户编号
     * @param request AI 对话请求
     * @param httpRequest HTTP 请求
     * @return AI 对话结果
     */
    @PostMapping("/chat/")
    public AiChatResponse chat(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {
        Object requestId = httpRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return service.chat(currentUserId, request, requestId == null ? "" : requestId.toString());
    }

    /**
     * 查询当前用户的 AI 会话列表。
     *
     * @param currentUserId 当前登录用户编号
     * @return AI 会话列表
     */
    @GetMapping("/conversations/")
    public List<AiConversationView> conversations(@CurrentUserId Long currentUserId) {
        return service.conversations(currentUserId);
    }

    /**
     * 创建当前用户的 AI 会话。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 会话创建请求
     * @return 创建后的 AI 会话
     */
    @PostMapping("/conversations/")
    public ResponseEntity<AiConversationView> create(
            @CurrentUserId Long currentUserId,
            @RequestBody(required = false) AiConversationRequest request) {
        String title = request == null ? null : request.title();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createConversation(currentUserId, title));
    }

    /**
     * 查询指定 AI 会话的消息记录。
     *
     * @param currentUserId 当前登录用户编号
     * @param conversationId AI 会话编号
     * @return 会话消息列表
     */
    @GetMapping("/conversations/{conversationId}/messages/")
    public List<AiMessageView> messages(
            @CurrentUserId Long currentUserId,
            @PathVariable Long conversationId) {
        return service.messages(currentUserId, conversationId);
    }
}