package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.AiConversation;
import com.bulongyu.housing.entity.AiMessage;
import com.bulongyu.housing.entity.HouseCandidate;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 客服数据访问接口
 */
@Mapper
public interface AiMapper {
    /**
     * 查询会话。
     *
     * @param id 编号
     * @param profileId 用户资料编号
     * @return 用户所属的 AI 会话；不存在时为 {@code null}
     */
    @Select("SELECT id,user_id,title,created_at,updated_at FROM ai_conversation WHERE id=#{id} AND user_id=#{profileId}")
    AiConversation findConversation(@Param("id") Long id, @Param("profileId") Long profileId);

    /**
     * 查询用户最近更新的 AI 会话。
     *
     * @param profileId 用户资料编号
     * @return 最近更新的 AI 会话；不存在时为 {@code null}
     */
    @Select("SELECT id,user_id,title,created_at,updated_at FROM ai_conversation WHERE user_id=#{profileId} ORDER BY updated_at DESC LIMIT 1")
    AiConversation findLatestConversation(Long profileId);

    /**
     * 查询用户最近的 AI 会话列表。
     *
     * @param profileId 用户资料编号
     * @return 用户的 AI 会话列表
     */
    @Select("SELECT id,user_id,title,created_at,updated_at FROM ai_conversation WHERE user_id=#{profileId} ORDER BY updated_at DESC LIMIT 20")
    List<AiConversation> findConversations(Long profileId);

    /**
     * 新增会话记录。
     *
     * @param conversation 会话
     * @return 受影响行数
     */
    @Insert("INSERT INTO ai_conversation(user_id,title,created_at,updated_at) VALUES(#{userId},#{title},#{createdAt},#{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConversation(MutableConversation conversation);

    /**
     * 更新会话。
     *
     * @param id 编号
     * @param title 标题
     * @param now 当前时间
     */
    @Update("UPDATE ai_conversation SET title=#{title},updated_at=#{now} WHERE id=#{id}")
    int updateConversation(@Param("id") Long id, @Param("title") String title, @Param("now") LocalDateTime now);

    /**
     * 查询会话最近的消息。
     *
     * @param conversationId 会话编号
     * @param limit 返回数量上限
     * @return 最近的消息列表
     */
    @Select("SELECT id,conversation_id,role,content,metadata,created_at FROM ai_message WHERE conversation_id=#{conversationId} ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<AiMessage> findRecentMessages(@Param("conversationId") Long conversationId, @Param("limit") int limit);

    /**
     * 新增消息记录。
     *
     * @param message 消息
     * @return 受影响行数
     */
    @Insert("INSERT INTO ai_message(conversation_id,role,content,metadata,created_at) VALUES(#{conversationId},#{role},#{content},#{metadata},#{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(MutableMessage message);

    /**
     * 根据候选编号和硬约束查询可推荐房源。
     *
     * @param candidateIds 候选数据编号集合
     * @param priceMin 最低租金
     * @param priceMax 最高租金
     * @param bedroomMin 最少卧室数量
     * @param bedroomMax 最多卧室数量
     * @param livingRoomMin 最少客厅数量
     * @param bathroomMin 最少卫生间数量
     * @param kitchenMin 最少厨房数量
     * @param region 地区名称
     */
    @Select("""
            <script>
            SELECT h.id,h.title,h.description,h.price,h.area,h.rooms,h.bedroom_count,
                   h.living_room_count,h.bathroom_count,h.kitchen_count,r.name region_name,
                   d.name district_name,c.name city_name,h.image
            FROM house h
            LEFT JOIN area r ON r.id=h.region_id
            LEFT JOIN area d ON d.id=r.parent_id
            LEFT JOIN area c ON c.id=d.parent_id
            WHERE h.status='approved' AND h.is_active=TRUE
            <if test='candidateIds != null and !candidateIds.isEmpty()'>
              AND h.id IN <foreach collection='candidateIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </if>
            <if test='priceMin != null'>AND h.price &gt;= #{priceMin}</if>
            <if test='priceMax != null'>AND h.price &lt;= #{priceMax}</if>
            <if test='bedroomMin != null'>AND h.bedroom_count &gt;= #{bedroomMin}</if>
            <if test='bedroomMax != null'>AND h.bedroom_count &lt;= #{bedroomMax}</if>
            <if test='livingRoomMin != null'>AND h.living_room_count &gt;= #{livingRoomMin}</if>
            <if test='bathroomMin != null'>AND h.bathroom_count &gt;= #{bathroomMin}</if>
            <if test='kitchenMin != null'>AND h.kitchen_count &gt;= #{kitchenMin}</if>
            <if test='region != null and region != ""'>
              AND (LOWER(r.name) LIKE LOWER(CONCAT('%',#{region},'%'))
                OR LOWER(d.name) LIKE LOWER(CONCAT('%',#{region},'%'))
                OR LOWER(c.name) LIKE LOWER(CONCAT('%',#{region},'%')))
            </if>
            </script>
            """)
    List<HouseCandidate> searchHouses(@Param("candidateIds") List<Long> candidateIds,
        @Param("priceMin") BigDecimal priceMin, @Param("priceMax") BigDecimal priceMax,
        @Param("bedroomMin") Integer bedroomMin, @Param("bedroomMax") Integer bedroomMax,
        @Param("livingRoomMin") Integer livingRoomMin, @Param("bathroomMin") Integer bathroomMin,
        @Param("kitchenMin") Integer kitchenMin, @Param("region") String region);

    /**
     * AI 客服Mutable会话数据模型
     */
    final class MutableConversation {
        public Long id; public Long userId; public String title;
        public LocalDateTime createdAt; public LocalDateTime updatedAt;
    }
    /**
     * AI 客服Mutable消息数据模型
     */
    final class MutableMessage {
        public Long id; public Long conversationId; public String role; public String content; public String metadata;
        public LocalDateTime createdAt;
    }
}
