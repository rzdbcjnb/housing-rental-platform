package com.bulongyu.housing.service.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 将数据库中的对话历史转换为 Spring AI 原生消息，保留用户和助手的真实角色。
 */
final class SpringAiMessages {

    private SpringAiMessages() {
    }

    /**
     * 按时间顺序转换历史消息，并在末尾追加当前用户问题。
     *
     * @param history 最近对话历史
     * @param userPrompt 当前用户问题
     * @return 可直接交给 ChatClient 的原生消息列表
     */
    static List<Message> from(List<AiModelGateway.ChatTurn> history, String userPrompt) {
        List<Message> messages = new ArrayList<>((history == null ? 0 : history.size()) + 1);
        if (history != null) {
            for (AiModelGateway.ChatTurn turn : history) {
                if (turn == null || turn.content() == null || turn.content().isBlank()) {
                    continue;
                }
                if ("assistant".equalsIgnoreCase(turn.role())) {
                    messages.add(new AssistantMessage(turn.content()));
                }
                else if ("user".equalsIgnoreCase(turn.role())) {
                    messages.add(new UserMessage(turn.content()));
                }
            }
        }
        messages.add(new UserMessage(userPrompt));
        return List.copyOf(messages);
    }
}