package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.dto.ChatCreateRoomRequest;
import com.bulongyu.housing.dto.ChatHouseShareRequest;
import com.bulongyu.housing.vo.ChatCountView;
import com.bulongyu.housing.vo.ChatDetailView;
import com.bulongyu.housing.vo.ChatHouseShareView;
import com.bulongyu.housing.vo.ChatMessagePage;
import com.bulongyu.housing.vo.ChatRoomPage;
import com.bulongyu.housing.vo.ChatRoomView;

import com.bulongyu.housing.service.ChatService;
import com.bulongyu.housing.websocket.ChatWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 实时聊天接口控制器
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final ChatWebSocketHandler webSocketHandler;

    /**
     * 初始化 {@code ChatController} 并注入所需依赖。
     *
     * @param chatService 聊天业务服务
     * @param webSocketHandler 聊天 WebSocket 处理器
     */
    public ChatController(ChatService chatService,
                          ChatWebSocketHandler webSocketHandler) {
        this.chatService = chatService;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 查询当前用户参与的聊天室列表。
     *
     * @param currentUserId 当前登录用户编号
     * @param page 页码
     * @param pageSize 页码每页数量
     */
    @GetMapping("/rooms/")
    ChatRoomPage rooms(@CurrentUserId Long currentUserId,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return chatService.rooms(currentUserId, page, pageSize);
    }

    /**
     * 创建或获取与指定用户的聊天室。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     * @return 创建或复用的聊天室信息
     */
    @PostMapping("/rooms/create/")
    ResponseEntity<ChatRoomView> create(@CurrentUserId Long currentUserId,
                                               @RequestBody ChatCreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.getOrCreate(currentUserId, request.userId(), request.houseId()));
    }

    /**
     * 查询指定聊天室详情并校验参与权限。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     */
    @GetMapping("/rooms/{id}/")
    ChatRoomView room(@CurrentUserId Long currentUserId, @PathVariable Long id) {
        return chatService.room(currentUserId, id);
    }

    /**
     * 创建或获取与指定用户的聊天室。
     *
     * @param currentUserId 当前登录用户编号
     * @param profileId 用户资料编号
     */
    @GetMapping("/rooms/with-user/{profileId}/")
    ChatRoomView withUser(@CurrentUserId Long currentUserId, @PathVariable Long profileId) {
        return chatService.getOrCreate(currentUserId, profileId, null);
    }

    /**
     * 查询指定会话或聊天室的消息记录。
     *
     * @param currentUserId 当前登录用户编号
     * @param roomId 聊天室编号
     * @param page 页码
     * @param pageSize 页码每页数量
     */
    @GetMapping("/rooms/{roomId}/messages/")
    ChatMessagePage messages(@CurrentUserId Long currentUserId, @PathVariable Long roomId,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return chatService.messages(currentUserId, roomId, page, pageSize);
    }

    /**
     * 将指定消息或通知标记为已读。
     *
     * @param currentUserId 当前登录用户编号
     * @param roomId 聊天室编号
     */
    @PostMapping("/rooms/{roomId}/read/")
    ChatDetailView markRead(@CurrentUserId Long currentUserId, @PathVariable Long roomId) {
        return chatService.markRead(currentUserId, roomId);
    }

    /**
     * 统计当前用户未读消息或通知数量。
     *
     * @param currentUserId 当前登录用户编号
     */
    @GetMapping("/unread-count/")
    ChatCountView unread(@CurrentUserId Long currentUserId) {
        return chatService.unread(currentUserId);
    }

    /**
     * 向聊天室发送房源分享消息。
     *
     * @param currentUserId 当前登录用户编号
     * @param roomId 聊天室编号
     * @param request 请求参数
     */
    @PostMapping("/rooms/{roomId}/share-house/")
    ResponseEntity<ChatHouseShareView> shareHouse(
            @CurrentUserId Long currentUserId, @PathVariable Long roomId,
            @RequestBody ChatHouseShareRequest request) {
        ChatHouseShareView response =
                chatService.shareHouse(currentUserId, roomId, request.houseId());
        webSocketHandler.broadcastHouseShare(roomId, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
