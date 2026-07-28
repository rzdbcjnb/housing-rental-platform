package com.bulongyu.housing.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 房源
 */
public class House {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private Integer area;
    private String rooms;
    private Integer bedroomCount;
    private Integer livingRoomCount;
    private Integer bathroomCount;
    private Integer kitchenCount;
    private String houseType;
    private Long regionId;
    private String addressDetail;
    private String image;
    private Long landlordId;
    private String status;
    private Integer clickCount;
    private Boolean active;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 房源
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 房源
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 房源
     * @return 标题
     */
    public String getTitle() { return title; }
    /**
     * 房源
     *
     * @param title 标题
     */
    public void setTitle(String title) { this.title = title; }
    /**
     * 房源
     * @return 房源描述
     */
    public String getDescription() { return description; }
    /**
     * 房源
     *
     * @param description 描述
     */
    public void setDescription(String description) { this.description = description; }
    /**
     * 房源
     * @return 租金
     */
    public BigDecimal getPrice() { return price; }
    /**
     * 房源
     *
     * @param price 租金
     */
    public void setPrice(BigDecimal price) { this.price = price; }
    /**
     * 房源
     * @return 地区
     */
    public Integer getArea() { return area; }
    /**
     * 房源
     *
     * @param area 地区
     */
    public void setArea(Integer area) { this.area = area; }
    /**
     * 房源
     * @return 户型描述
     */
    public String getRooms() { return rooms; }
    /**
     * 房源
     *
     * @param rooms 户型描述
     */
    public void setRooms(String rooms) { this.rooms = rooms; }
    /**
     * 房源
     * @return 符合条件的数据数量
     */
    public Integer getBedroomCount() { return bedroomCount; }
    /**
     * 房源
     *
     * @param bedroomCount 卧室数量
     */
    public void setBedroomCount(Integer bedroomCount) { this.bedroomCount = bedroomCount; }
    /**
     * 房源
     * @return 符合条件的数据数量
     */
    public Integer getLivingRoomCount() { return livingRoomCount; }
    /**
     * 房源
     *
     * @param livingRoomCount 客厅数量
     */
    public void setLivingRoomCount(Integer livingRoomCount) { this.livingRoomCount = livingRoomCount; }
    /**
     * 房源
     * @return 符合条件的数据数量
     */
    public Integer getBathroomCount() { return bathroomCount; }
    /**
     * 房源
     *
     * @param bathroomCount 卫生间数量
     */
    public void setBathroomCount(Integer bathroomCount) { this.bathroomCount = bathroomCount; }
    /**
     * 房源
     * @return 符合条件的数据数量
     */
    public Integer getKitchenCount() { return kitchenCount; }
    /**
     * 房源
     *
     * @param kitchenCount 厨房数量
     */
    public void setKitchenCount(Integer kitchenCount) { this.kitchenCount = kitchenCount; }
    /**
     * 房源
     * @return 房源类型
     */
    public String getHouseType() { return houseType; }
    /**
     * 房源
     *
     * @param houseType 房源类型
     */
    public void setHouseType(String houseType) { this.houseType = houseType; }
    /**
     * 房源
     * @return 地区编号
     */
    public Long getRegionId() { return regionId; }
    /**
     * 房源
     *
     * @param regionId 地区编号
     */
    public void setRegionId(Long regionId) { this.regionId = regionId; }
    /**
     * 房源
     * @return 房源详细地址
     */
    public String getAddressDetail() { return addressDetail; }
    /**
     * 房源
     *
     * @param addressDetail 房源详细地址
     */
    public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }
    /**
     * 房源
     * @return 图片文件
     */
    public String getImage() { return image; }
    /**
     * 房源
     *
     * @param image 图片文件
     */
    public void setImage(String image) { this.image = image; }
    /**
     * 房源
     * @return 房东编号
     */
    public Long getLandlordId() { return landlordId; }
    /**
     * 房源
     *
     * @param landlordId 房东编号
     */
    public void setLandlordId(Long landlordId) { this.landlordId = landlordId; }
    /**
     * 房源
     * @return 状态
     */
    public String getStatus() { return status; }
    /**
     * 房源
     *
     * @param status 状态
     */
    public void setStatus(String status) { this.status = status; }
    /**
     * 房源
     * @return 符合条件的数据数量
     */
    public Integer getClickCount() { return clickCount; }
    /**
     * 房源
     *
     * @param clickCount 房源点击次数
     */
    public void setClickCount(Integer clickCount) { this.clickCount = clickCount; }
    /**
     * 房源
     * @return 是否启用
     */
    public Boolean getActive() { return active; }
    /**
     * 房源
     *
     * @param active 是否启用
     */
    public void setActive(Boolean active) { this.active = active; }
    /**
     * 房源
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() { return createTime; }
    /**
     * 房源
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    /**
     * 房源
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() { return updateTime; }
    /**
     * 房源
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
