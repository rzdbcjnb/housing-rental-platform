package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 聊天聊天室
 */
public class ChatRoom {
    private Long id;
    private String roomType;
    private String name;
    private Long houseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 聊天聊天室
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 聊天聊天室
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 聊天聊天室
     * @return 聊天室类型
     */
    public String getRoomType() { return roomType; }
    /**
     * 聊天聊天室
     *
     * @param roomType 聊天室类型
     */
    public void setRoomType(String roomType) { this.roomType = roomType; }
    /**
     * 聊天聊天室
     * @return 名称
     */
    public String getName() { return name; }
    /**
     * 聊天聊天室
     *
     * @param name 名称
     */
    public void setName(String name) { this.name = name; }
    /**
     * 聊天聊天室
     * @return 房源编号
     */
    public Long getHouseId() { return houseId; }
    /**
     * 聊天聊天室
     *
     * @param houseId 房源编号
     */
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    /**
     * 聊天聊天室
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /**
     * 聊天聊天室
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /**
     * 聊天聊天室
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /**
     * 聊天聊天室
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
