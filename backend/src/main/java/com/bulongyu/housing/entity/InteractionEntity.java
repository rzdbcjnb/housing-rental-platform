package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 收藏与浏览历史实体
 */
public class InteractionEntity {
    private Long id;
    private Long userId;
    private Long houseId;
    private LocalDateTime createTime;

    /**
     * 收藏与浏览历史实体
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 收藏与浏览历史实体
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 收藏与浏览历史实体
     * @return 用户编号
     */
    public Long getUserId() { return userId; }
    /**
     * 收藏与浏览历史实体
     *
     * @param userId 用户编号
     */
    public void setUserId(Long userId) { this.userId = userId; }
    /**
     * 收藏与浏览历史实体
     * @return 房源编号
     */
    public Long getHouseId() { return houseId; }
    /**
     * 收藏与浏览历史实体
     *
     * @param houseId 房源编号
     */
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    /**
     * 收藏与浏览历史实体
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() { return createTime; }
    /**
     * 收藏与浏览历史实体
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
