package com.bulongyu.housing.service.ai;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 客服外部能力访问网关
 */
@Component
public class SpringAiModelGateway implements AiModelGateway {
    private final ObjectProvider<ChatModel> models;

    /**
     * 初始化 {@code SpringAiModelGateway} 并注入所需依赖。
     *
     * @param models 可选的聊天模型提供器
     */
    public SpringAiModelGateway(ObjectProvider<ChatModel> models) {
        this.models = models;
    }

    /**
     * 检查大模型或向量检索能力是否可用。
     * @return 条件成立时返回 true，否则返回 false
     */
    @Override public boolean available() {
        return models.getIfAvailable() != null; }

    /**
     * 调用大模型生成对话回复。
     *
     * @param systemPrompt 系统提示词
     * @param history 用于保持上下文的历史消息
     * @param userPrompt 用户提示词
     */
    @Override
    public String complete(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        ChatModel model = models.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("AI chat model is not configured");
        }
        return ChatClient.builder(model).build().prompt()
                .system(systemPrompt)
                .messages(SpringAiMessages.from(history, userPrompt))
                .call()
                .content();
    }
}
