package com.bulongyu.housing.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付Record
 */
public class PaymentRecord {
    private Long id;
    private Long userId;
    private Long houseId;
    private BigDecimal amount;
    private Boolean paid;
    private LocalDateTime createdAt;

    /**
     * 支付Record
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 支付Record
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 支付Record
     * @return 用户编号
     */
    public Long getUserId() { return userId; }
    /**
     * 支付Record
     *
     * @param userId 用户编号
     */
    public void setUserId(Long userId) { this.userId = userId; }
    /**
     * 支付Record
     * @return 房源编号
     */
    public Long getHouseId() { return houseId; }
    /**
     * 支付Record
     *
     * @param houseId 房源编号
     */
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    /**
     * 支付Record
     * @return 金额
     */
    public BigDecimal getAmount() { return amount; }
    /**
     * 支付Record
     *
     * @param amount 金额
     */
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    /**
     * 支付Record
     * @return 是否已支付
     */
    public Boolean getPaid() { return paid; }
    /**
     * 支付Record
     *
     * @param paid 已支付金额
     */
    public void setPaid(Boolean paid) { this.paid = paid; }
    /**
     * 支付Record
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /**
     * 支付Record
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
