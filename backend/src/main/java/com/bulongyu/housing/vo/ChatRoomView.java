package com.bulongyu.housing.vo;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 聊天聊天室返回数据
 */
public record ChatRoomView(Long id, String roomType, String name, Long house,
                       ChatHouseView houseInfo, ChatOtherUserView otherUser, ChatLastMessageView lastMessage,
                       Long unreadCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
