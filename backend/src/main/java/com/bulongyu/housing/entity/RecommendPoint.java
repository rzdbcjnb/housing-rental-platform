package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 推荐积分
 */
public class RecommendPoint {
    private Long id;
    private Long houseId;
    private Integer points;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 推荐积分
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 推荐积分
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 推荐积分
     * @return 房源编号
     */
    public Long getHouseId() { return houseId; }
    /**
     * 推荐积分
     *
     * @param houseId 房源编号
     */
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    /**
     * 推荐积分
     * @return 积分
     */
    public Integer getPoints() { return points; }
    /**
     * 推荐积分
     *
     * @param points 积分
     */
    public void setPoints(Integer points) { this.points = points; }
    /**
     * 推荐积分
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /**
     * 推荐积分
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /**
     * 推荐积分
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /**
     * 推荐积分
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
