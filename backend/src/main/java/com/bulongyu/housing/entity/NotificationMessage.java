package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 通知消息
 */
public class NotificationMessage {
    private Long id;
    private Long recipientId;
    private Long senderId;
    private String messageType;
    private String title;
    private String content;
    private Boolean read;
    private Long relatedHouseId;
    private LocalDateTime createTime;

    /**
     * 通知消息
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 通知消息
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 通知消息
     * @return 通知接收者编号
     */
    public Long getRecipientId() { return recipientId; }
    /**
     * 通知消息
     *
     * @param recipientId 接收者编号
     */
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    /**
     * 通知消息
     * @return 消息发送者编号
     */
    public Long getSenderId() { return senderId; }
    /**
     * 通知消息
     *
     * @param senderId 发送者编号
     */
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    /**
     * 通知消息
     * @return 消息类型
     */
    public String getMessageType() { return messageType; }
    /**
     * 通知消息
     *
     * @param messageType 消息类型
     */
    public void setMessageType(String messageType) { this.messageType = messageType; }
    /**
     * 通知消息
     * @return 标题
     */
    public String getTitle() { return title; }
    /**
     * 通知消息
     *
     * @param title 标题
     */
    public void setTitle(String title) { this.title = title; }
    /**
     * 通知消息
     * @return 内容
     */
    public String getContent() { return content; }
    /**
     * 通知消息
     *
     * @param content 内容
     */
    public void setContent(String content) { this.content = content; }
    /**
     * 通知消息
     * @return 是否已读
     */
    public Boolean getRead() { return read; }
    /**
     * 通知消息
     *
     * @param read 是否已读
     */
    public void setRead(Boolean read) { this.read = read; }
    /**
     * 通知消息
     * @return 关联房源编号
     */
    public Long getRelatedHouseId() { return relatedHouseId; }
    /**
     * 通知消息
     *
     * @param relatedHouseId 关联房源编号
     */
    public void setRelatedHouseId(Long relatedHouseId) { this.relatedHouseId = relatedHouseId; }
    /**
     * 通知消息
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() { return createTime; }
    /**
     * 通知消息
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
