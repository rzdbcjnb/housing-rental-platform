package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.AgentContext;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import com.bulongyu.housing.service.ChatService;
import com.bulongyu.housing.service.HouseService;
import com.bulongyu.housing.service.InteractionService;
import com.bulongyu.housing.vo.ChatHouseShareView;
import com.bulongyu.housing.vo.ChatMessageView;
import com.bulongyu.housing.vo.FavoriteStatus;
import com.bulongyu.housing.vo.HouseDetailView;
import com.bulongyu.housing.vo.InteractionCreatedView;
import com.bulongyu.housing.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiActionServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private HouseService houseService;
    private InteractionService interactionService;
    private ChatService chatService;
    private ChatWebSocketHandler webSocketHandler;
    private UserMapper userMapper;
    private AiActionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        houseService = mock(HouseService.class);
        interactionService = mock(InteractionService.class);
        chatService = mock(ChatService.class);
        webSocketHandler = mock(ChatWebSocketHandler.class);
        userMapper = mock(UserMapper.class);
        ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
        service = new AiActionService(
                redis,
                json,
                houseService,
                interactionService,
                chatService,
                webSocketHandler,
                userMapper);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(houseService.detail(10L, 1L)).thenReturn(house(10L, 20L));
        when(interactionService.favoriteStatus(1L, 10L))
                .thenReturn(new FavoriteStatus(false, null));
        when(userMapper.findProfileByUserId(1L)).thenReturn(profile(1L, 11L));
    }

    @Test
    void prepareFavoriteOnlyStoresPendingAction() {
        var pending = service.prepareFavorite(context(), 10L);

        assertThat(pending.action()).isEqualTo(AiActionService.FAVORITE_ACTION);
        assertThat(pending.house().id()).isEqualTo(10L);
        assertThat(pending.expiresAt()).isAfter(LocalDateTime.now().plusMinutes(4));
        verify(values).setIfAbsent(
                eq("ai:action:" + pending.token()),
                anyString(),
                eq(Duration.ofMinutes(5)));
        verify(interactionService, never()).addFavorite(any(), any());
    }

    @Test
    void confirmsFavoriteExactlyOnce() {
        var pending = service.prepareFavorite(context(), 10L);
        String storedValue = storedValue();
        when(values.get("ai:action:" + pending.token())).thenReturn(storedValue);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        when(interactionService.addFavorite(1L, 10L)).thenReturn(
                new InteractionCreatedView(88L, 10L, LocalDateTime.now()));

        var result = service.confirm(1L, 2L, pending.token());

        assertThat(result.favoriteId()).isEqualTo(88L);
        verify(interactionService).addFavorite(1L, 10L);
    }

    @Test
    void rejectsExpiredAndCrossUserTokens() {
        String missingToken = "8d40aa79-d2c4-4622-bad8-524535001112";
        when(values.get("ai:action:" + missingToken)).thenReturn(null);

        assertThatThrownBy(() -> service.confirm(1L, 2L, missingToken))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("AI_ACTION_EXPIRED_OR_USED");

        var pending = service.prepareFavorite(context(), 10L);
        String crossUserValue = storedValue();
        when(values.get("ai:action:" + pending.token())).thenReturn(crossUserValue);
        assertThatThrownBy(() -> service.confirm(99L, 2L, pending.token()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("AI_ACTION_FORBIDDEN");
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void rejectsTamperedAndRepeatedConfirmations() {
        var pending = service.prepareFavorite(context(), 10L);
        String storedValue = storedValue();
        String tamperedValue = storedValue.replace("\"houseId\":10", "\"houseId\":11");
        when(values.get("ai:action:" + pending.token())).thenReturn(tamperedValue);

        assertThatThrownBy(() -> service.confirm(1L, 2L, pending.token()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("AI_ACTION_TAMPERED");

        when(values.get("ai:action:" + pending.token())).thenReturn(storedValue);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);
        assertThatThrownBy(() -> service.confirm(1L, 2L, pending.token()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("AI_ACTION_ALREADY_USED");
    }

    @Test
    void sendsExactConfirmedTextAndHouseCardThenBroadcasts() {
        String content = "您好，可以发一下这套房子的更多细节图吗？我想进一步了解。";
        var pending = service.prepareSendLandlordMessage(context(), 10L, "  " + content + "  ");
        String inquiryValue = storedValue();
        when(values.get("ai:action:" + pending.token())).thenReturn(inquiryValue);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        ChatService.HouseInquiryResult inquiry = new ChatService.HouseInquiryResult(
                30L,
                new ChatMessageView(
                        31L, 30L, 11L, 1L, "tenant", "", "text",
                        content, false, LocalDateTime.now()),
                new ChatHouseShareView(32L, "house_share", Map.of("id", 10L)));
        when(chatService.sendHouseInquiry(1L, 10L, content)).thenReturn(inquiry);

        var result = service.confirm(1L, 2L, pending.token());

        assertThat(result.roomId()).isEqualTo(30L);
        assertThat(result.textMessageId()).isEqualTo(31L);
        assertThat(result.houseShareMessageId()).isEqualTo(32L);
        verify(chatService).sendHouseInquiry(1L, 10L, content);
        verify(webSocketHandler).broadcastHouseInquiry(inquiry);
    }

    @Test
    void doesNotExecuteWriteOperationWhenRedisIsUnavailable() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatThrownBy(() -> service.prepareFavorite(context(), 10L))
                .isInstanceOf(RedisConnectionFailureException.class);
        verify(interactionService, never()).addFavorite(any(), any());
        verify(chatService, never()).sendHouseInquiry(any(), any(), any());
    }
    private String storedValue() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(anyString(), captor.capture(), any(Duration.class));
        return captor.getValue();
    }

    private AgentContext context() {
        return new AgentContext(1L, 2L, "request-1");
    }

    private UserProfile profile(Long userId, Long profileId) {
        LocalDateTime now = LocalDateTime.now();
        return new UserProfile(profileId, userId, "13800000000", "tenant", "", now, now);
    }

    private HouseDetailView house(Long houseId, Long landlordProfileId) {
        LocalDateTime now = LocalDateTime.now();
        return new HouseDetailView(
                houseId,
                "测试房源",
                "公开描述",
                new BigDecimal("2000"),
                80,
                "2室1厅1卫1厨",
                2,
                1,
                1,
                1,
                "whole",
                "整租",
                3L,
                "大连-甘井子区",
                "敏感详细地址",
                "",
                landlordProfileId,
                null,
                "approved",
                "已通过",
                true,
                now,
                now);
    }
}
