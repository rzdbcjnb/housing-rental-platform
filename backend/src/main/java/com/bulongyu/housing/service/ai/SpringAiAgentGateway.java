package com.bulongyu.housing.service.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 基于 Spring AI ChatClient 的 Tool Calling 网关。
 */
@Component
public class SpringAiAgentGateway implements AiAgentGateway {
    private final ObjectProvider<ChatModel> models;

    /**
     * 初始化 Spring AI Agent 网关。
     *
     * @param models 可选的聊天模型提供器
     */
    public SpringAiAgentGateway(ObjectProvider<ChatModel> models) {
        this.models = models;
    }

    /**
     * 判断聊天模型是否可用。
     *
     * @return 模型可用时返回 true
     */
    @Override
    public boolean available() {
        return models.getIfAvailable() != null;
    }

    /**
     * 注册白名单工具并执行由 ChatClient 管理的工具调用循环。
     *
     * @param systemPrompt Agent 系统提示词
     * @param history 最近对话历史
     * @param userPrompt 当前用户问题
     * @param tools 服务端白名单工具对象
     * @param toolContext 服务端可信工具上下文
     * @return 模型最终回复
     */
    @Override
    public String complete(String systemPrompt,
                           List<AiModelGateway.ChatTurn> history,
                           String userPrompt,
                           Object[] tools,
                           Map<String, Object> toolContext) {
        ChatModel model = models.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("AI chat model is not configured");
        }
        return ChatClient.builder(model)
                .build()
                .prompt()
                .system(systemPrompt)
                .messages(SpringAiMessages.from(history, userPrompt))
                .tools(tools)
                .toolContext(toolContext)
                .call()
                .content();
    }

}
