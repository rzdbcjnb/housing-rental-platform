package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.ChatMessage;
import com.bulongyu.housing.entity.ChatRoom;
import com.bulongyu.housing.entity.ChatMessageRow;
import com.bulongyu.housing.entity.RoomRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实时聊天数据访问接口
 */
@Mapper
public interface ChatMapper {
    /**
     * 统计用户参与的聊天室总数。
     *
     * @param profileId 用户资料编号
     * @return 符合条件的数据数量
     */
    long countRooms(Long profileId);
    /**
     * 分页查询用户参与的聊天室。
     *
     * @param profileId 用户资料编号
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页聊天室列表
     */
    List<RoomRow> findRooms(@Param("profileId") Long profileId,
                            @Param("offset") int offset, @Param("limit") int limit);
    /**
     * 查询聊天室。
     *
     * @param roomId 聊天室编号
     * @param profileId 用户资料编号
     * @return 用户可访问的聊天室；不存在时为 {@code null}
     */
    RoomRow findRoom(@Param("roomId") Long roomId, @Param("profileId") Long profileId);
    /**
     * 查询两名用户之间已存在的私聊房间。
     *
     * @param firstProfileId first用户资料编号
     * @param secondProfileId second用户资料编号
     * @return 已存在的私聊房间编号；不存在时为 {@code null}
     */
    Long findExistingPrivateRoom(@Param("firstProfileId") Long firstProfileId,
                                 @Param("secondProfileId") Long secondProfileId);
    /**
     * 新增聊天室记录。
     *
     * @param room 聊天室
     * @return 受影响行数
     */
    int insertRoom(ChatRoom room);
    /**
     * 新增聊天室参与者记录。
     *
     * @param roomId 聊天室编号
     * @param profileId 用户资料编号
     * @return 受影响行数
     */
    int insertParticipant(@Param("roomId") Long roomId, @Param("profileId") Long profileId);
    /**
     * 更新聊天室最后活跃时间。
     *
     * @param roomId 聊天室编号
     * @param updatedAt 更新时间
     */
    int touchRoom(@Param("roomId") Long roomId, @Param("updatedAt") LocalDateTime updatedAt);
    /**
     * 统计聊天室参与者数量。
     *
     * @param roomId 聊天室编号
     * @param profileId 用户资料编号
     * @return 符合条件的数据数量
     */
    int countParticipant(@Param("roomId") Long roomId, @Param("profileId") Long profileId);

    /**
     * 统计聊天室消息总数。
     *
     * @param roomId 聊天室编号
     * @return 符合条件的数据数量
     */
    long countMessages(Long roomId);
    /**
     * 分页查询聊天室消息。
     *
     * @param roomId 聊天室编号
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页聊天消息列表
     */
    List<ChatMessageRow> findMessages(@Param("roomId") Long roomId,
                                  @Param("offset") int offset, @Param("limit") int limit);
    /**
     * 查询聊天室中的指定消息编号。
     *
     * @param roomId 聊天室编号
     * @param ids 编号集合
     * @return 属于该聊天室的消息编号集合
     */
    List<ChatMessageRow> findMessagesByIds(@Param("roomId") Long roomId,
                                       @Param("ids") List<Long> ids);
    /**
     * 新增消息记录。
     *
     * @param message 消息
     * @return 受影响行数
     */
    int insertMessage(ChatMessage message);
    /**
     * 将指定消息或通知标记为已读。
     *
     * @param roomId 聊天室编号
     * @param readerProfileId reader用户资料编号
     */
    int markRead(@Param("roomId") Long roomId, @Param("readerProfileId") Long readerProfileId);
    /**
     * 批量将指定聊天消息标记为已读。
     *
     * @param roomId 聊天室编号
     * @param readerProfileId reader用户资料编号
     * @param ids 编号集合
     */
    int markReadByIds(@Param("roomId") Long roomId, @Param("readerProfileId") Long readerProfileId,
                      @Param("ids") List<Long> ids);
    /**
     * 统计当前用户的未读消息数量。
     *
     * @param profileId 用户资料编号
     * @return 符合条件的数据数量
     */
    long unreadCount(Long profileId);

    /**
     * 查询用户在线状态记录编号。
     *
     * @param profileId 用户资料编号
     * @return 在线状态记录编号；不存在时为 {@code null}
     */
    Long findOnlineStatusId(Long profileId);
    /**
     * 新增用户在线状态记录。
     *
     * @param profileId 用户资料编号
     * @param online 是否在线
     * @param lastSeen 最后在线时间
     * @return 受影响行数
     */
    int insertOnlineStatus(@Param("profileId") Long profileId, @Param("online") boolean online,
                           @Param("lastSeen") LocalDateTime lastSeen);
    /**
     * 更新用户在线状态。
     *
     * @param id 编号
     * @param online 是否在线
     * @param lastSeen 最后在线时间
     */
    int updateOnlineStatus(@Param("id") Long id, @Param("online") boolean online,
                           @Param("lastSeen") LocalDateTime lastSeen);
}
