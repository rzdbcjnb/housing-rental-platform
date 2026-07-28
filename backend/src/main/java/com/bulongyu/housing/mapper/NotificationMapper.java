package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.Announcement;
import com.bulongyu.housing.entity.AnnouncementRow;
import com.bulongyu.housing.entity.NotificationMessageRow;
import com.bulongyu.housing.entity.NotificationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站内通知数据访问接口
 */
@Mapper
public interface NotificationMapper {
    /**
     * 统计符合条件的站内通知总数。
     *
     * @param recipientId 接收者编号
     * @param type 类型
     * @param read 是否已读
     * @return 符合条件的数据数量
     */
    long countMessages(@Param("recipientId") Long recipientId, @Param("type") String type,
                       @Param("read") Boolean read);
    /**
     * 分页查询用户站内通知。
     *
     * @param recipientId 接收者编号
     * @param type 类型
     * @param read 是否已读
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页站内通知
     */
    List<NotificationMessageRow> findMessages(@Param("recipientId") Long recipientId, @Param("type") String type,
                                  @Param("read") Boolean read, @Param("offset") int offset,
                                  @Param("limit") int limit);
    /**
     * 统计当前用户的未读消息数量。
     *
     * @param recipientId 接收者编号
     * @return 符合条件的数据数量
     */
    long unreadCount(Long recipientId);
    /**
     * 新增消息记录。
     *
     * @param message 消息
     * @return 受影响行数
     */
    int insertMessage(NotificationMessage message);
    /**
     * 标记消息是否已读。
     *
     * @param id 编号
     * @param recipientId 接收者编号
     */
    int markMessageRead(@Param("id") Long id, @Param("recipientId") Long recipientId);
    /**
     * 将当前用户的全部通知标记为已读。
     *
     * @param recipientId 接收者编号
     */
    int markAllRead(Long recipientId);
    /**
     * 删除消息。
     *
     * @param id 编号
     * @param recipientId 接收者编号
     */
    int deleteMessage(@Param("id") Long id, @Param("recipientId") Long recipientId);
    /**
     * 查询全部用户资料编号。
     * @return 全部用户资料编号集合
     */
    List<Long> findAllProfileIds();
    /**
     * 查询管理员用户资料编号。
     * @return 管理员用户资料编号集合
     */
    List<Long> findAdminProfileIds();

    /**
     * 统计系统公告总数。
     * @return 符合条件的数据数量
     */
    long countAnnouncements();
    /**
     * 分页查询系统公告。
     *
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页系统公告
     */
    List<AnnouncementRow> findAnnouncements(@Param("offset") int offset, @Param("limit") int limit);
    /**
     * 查询公告。
     *
     * @param id 编号
     * @return 公告信息；不存在时为 {@code null}
     */
    AnnouncementRow findAnnouncement(Long id);
    /**
     * 新增公告记录。
     *
     * @param announcement 公告
     * @return 受影响行数
     */
    int insertAnnouncement(Announcement announcement);
    /**
     * 更新系统公告内容。
     *
     * @param announcement 公告
     */
    int updateAnnouncement(Announcement announcement);
    /**
     * 删除指定系统公告。
     *
     * @param id 编号
     */
    int deleteAnnouncement(Long id);
    /**
     * 批量删除系统公告。
     *
     * @param ids 编号集合
     */
    int deleteAnnouncements(@Param("ids") List<Long> ids);
}
