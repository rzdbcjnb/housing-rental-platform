package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 聊天消息
 */
public class ChatMessage {
    private Long id;
    private Long roomId;
    private Long senderId;
    private String messageType;
    private String content;
    private Boolean read;
    private Boolean deleted;
    private Boolean recalled;
    private String recallReason;
    private LocalDateTime recalledAt;
    private LocalDateTime createdAt;

    /**
     * 聊天消息
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 聊天消息
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 聊天消息
     * @return 聊天室编号
     */
    public Long getRoomId() { return roomId; }
    /**
     * 聊天消息
     *
     * @param roomId 聊天室编号
     */
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    /**
     * 聊天消息
     * @return 消息发送者编号
     */
    public Long getSenderId() { return senderId; }
    /**
     * 聊天消息
     *
     * @param senderId 发送者编号
     */
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    /**
     * 聊天消息
     * @return 消息类型
     */
    public String getMessageType() { return messageType; }
    /**
     * 聊天消息
     *
     * @param messageType 消息类型
     */
    public void setMessageType(String messageType) { this.messageType = messageType; }
    /**
     * 聊天消息
     * @return 内容
     */
    public String getContent() { return content; }
    /**
     * 聊天消息
     *
     * @param content 内容
     */
    public void setContent(String content) { this.content = content; }
    /**
     * 聊天消息
     * @return 是否已读
     */
    public Boolean getRead() { return read; }
    /**
     * 聊天消息
     *
     * @param read 是否已读
     */
    public void setRead(Boolean read) { this.read = read; }
    /**
     * 聊天消息
     * @return 是否已删除
     */
    public Boolean getDeleted() { return deleted; }
    /**
     * 聊天消息
     *
     * @param deleted 是否删除
     */
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    /**
     * 聊天消息
     * @return 是否已撤回
     */
    public Boolean getRecalled() { return recalled; }
    /**
     * 聊天消息
     *
     * @param recalled 是否撤回
     */
    public void setRecalled(Boolean recalled) { this.recalled = recalled; }
    /**
     * 聊天消息
     * @return 消息撤回原因
     */
    public String getRecallReason() { return recallReason; }
    /**
     * 聊天消息
     *
     * @param recallReason 撤回原因
     */
    public void setRecallReason(String recallReason) { this.recallReason = recallReason; }
    /**
     * 聊天消息
     * @return 消息撤回时间
     */
    public LocalDateTime getRecalledAt() { return recalledAt; }
    /**
     * 聊天消息
     *
     * @param recalledAt 撤回时间
     */
    public void setRecalledAt(LocalDateTime recalledAt) { this.recalledAt = recalledAt; }
    /**
     * 聊天消息
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /**
     * 聊天消息
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
