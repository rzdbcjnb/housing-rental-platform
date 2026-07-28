package com.bulongyu.housing.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiMessagesTest {

    @Test
    void preservesRolesAndAppendsCurrentQuestion() {
        List<AiModelGateway.ChatTurn> history = List.of(
                new AiModelGateway.ChatTurn("user", "房源151"),
                new AiModelGateway.ChatTurn("assistant", "这是房源151的详情"));

        var messages = SpringAiMessages.from(history, "帮我联系房东");

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("房源151");
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("这是房源151的详情");
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo("帮我联系房东");
    }

    @Test
    void ignoresUnsupportedAndBlankHistoryEntries() {
        List<AiModelGateway.ChatTurn> history = List.of(
                new AiModelGateway.ChatTurn("system", "不能混入系统消息"),
                new AiModelGateway.ChatTurn("assistant", " "));

        var messages = SpringAiMessages.from(history, "你好");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("你好");
    }
}