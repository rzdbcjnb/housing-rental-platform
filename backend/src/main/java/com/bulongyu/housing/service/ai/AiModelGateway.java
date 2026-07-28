package com.bulongyu.housing.service.ai;

import java.util.List;

/**
 * AI 大模型调用网关
 */
public interface AiModelGateway {
    /**
     * AI 大模型调用网关
     * @return 条件成立时返回 true，否则返回 false
     */
    boolean available();
    /**
     * AI 大模型调用网关
     *
     * @param systemPrompt 系统提示词
     * @param history 用于保持上下文的历史消息
     * @param userPrompt 用户提示词
     */
    String complete(String systemPrompt, List<ChatTurn> history, String userPrompt);

    /**
     * AI 大模型调用网关
     */
    record ChatTurn(String role, String content) {}
}
