package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 积分账户
 */
public class PointAccount {
    private Long id;
    private Long userId;
    private Integer balance;
    private Integer totalPurchased;
    private Integer totalInvested;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 积分账户
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 积分账户
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 积分账户
     * @return 用户编号
     */
    public Long getUserId() { return userId; }
    /**
     * 积分账户
     *
     * @param userId 用户编号
     */
    public void setUserId(Long userId) { this.userId = userId; }
    /**
     * 积分账户
     * @return 账户余额
     */
    public Integer getBalance() { return balance; }
    /**
     * 积分账户
     *
     * @param balance 账户余额
     */
    public void setBalance(Integer balance) { this.balance = balance; }
    /**
     * 积分账户
     * @return 累计购买积分
     */
    public Integer getTotalPurchased() { return totalPurchased; }
    /**
     * 积分账户
     *
     * @param totalPurchased 累计购买数量
     */
    public void setTotalPurchased(Integer totalPurchased) { this.totalPurchased = totalPurchased; }
    /**
     * 积分账户
     * @return 累计投入推荐的积分
     */
    public Integer getTotalInvested() { return totalInvested; }
    /**
     * 积分账户
     *
     * @param totalInvested 累计投入积分
     */
    public void setTotalInvested(Integer totalInvested) { this.totalInvested = totalInvested; }
    /**
     * 积分账户
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /**
     * 积分账户
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /**
     * 积分账户
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /**
     * 积分账户
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
