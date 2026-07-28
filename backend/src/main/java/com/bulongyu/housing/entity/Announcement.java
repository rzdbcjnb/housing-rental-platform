package com.bulongyu.housing.entity;

import java.time.LocalDateTime;

/**
 * 公告
 */
public class Announcement {
    private Long id;
    private String title;
    private String content;
    private Long authorId;
    private Boolean active;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 公告
     * @return 编号
     */
    public Long getId() { return id; }
    /**
     * 公告
     *
     * @param id 编号
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 公告
     * @return 标题
     */
    public String getTitle() { return title; }
    /**
     * 公告
     *
     * @param title 标题
     */
    public void setTitle(String title) { this.title = title; }
    /**
     * 公告
     * @return 内容
     */
    public String getContent() { return content; }
    /**
     * 公告
     *
     * @param content 内容
     */
    public void setContent(String content) { this.content = content; }
    /**
     * 公告
     * @return 公告发布者编号
     */
    public Long getAuthorId() { return authorId; }
    /**
     * 公告
     *
     * @param authorId 公告发布者编号
     */
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    /**
     * 公告
     * @return 是否启用
     */
    public Boolean getActive() { return active; }
    /**
     * 公告
     *
     * @param active 是否启用
     */
    public void setActive(Boolean active) { this.active = active; }
    /**
     * 公告
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() { return createTime; }
    /**
     * 公告
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    /**
     * 公告
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() { return updateTime; }
    /**
     * 公告
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
