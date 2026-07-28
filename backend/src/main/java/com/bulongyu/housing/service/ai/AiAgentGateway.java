package com.bulongyu.housing.service.ai;

import java.util.List;
import java.util.Map;

/**
 * 支持白名单工具调用的 AI 模型网关。
 */
public interface AiAgentGateway {
    /**
     * 判断 Agent 模型是否已经配置。
     *
     * @return 模型可用时返回 true
     */
    boolean available();

    /**
     * 调用模型并允许模型在本轮对话中选择已注册工具。
     *
     * @param systemPrompt Agent 系统提示词
     * @param history 最近对话历史
     * @param userPrompt 当前用户问题
     * @param tools 服务端白名单工具对象
     * @param toolContext 服务端可信工具上下文
     * @return 模型最终回复
     */
    String complete(String systemPrompt,
                    List<AiModelGateway.ChatTurn> history,
                    String userPrompt,
                    Object[] tools,
                    Map<String, Object> toolContext);
}
