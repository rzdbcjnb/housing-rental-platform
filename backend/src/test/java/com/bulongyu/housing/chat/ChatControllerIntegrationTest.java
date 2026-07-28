package com.bulongyu.housing.chat;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.service.ChatService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ChatService chatService;
    Long firstUser;
    Long firstProfile;
    Long secondUser;
    Long secondProfile;
    Long outsiderUser;
    Long houseId;

    @BeforeEach
    void seed() {
        clean();
        firstUser = user("tenant", "tenant", "13800000001");
        firstProfile = profile(firstUser);
        secondUser = user("landlord", "landlord", "13800000002");
        secondProfile = profile(secondUser);
        outsiderUser = user("outsider", "tenant", "13800000003");
        db.update("INSERT INTO area (name,parent_id,level,is_active) VALUES ('Dalian',NULL,1,TRUE)");
        Long region = db.queryForObject("SELECT id FROM area WHERE name='Dalian'", Long.class);
        db.update("""
                INSERT INTO house (title,description,price,area,rooms,bedroom_count,
                    living_room_count,bathroom_count,kitchen_count,house_type,region_id,
                    address_detail,image,landlord_id,status,click_count,is_active,
                    create_time,update_time)
                VALUES ('chat-house','',1800,70,'2\u5ba41\u53851\u536b1\u53a8',2,1,1,1,'whole',?,'','',?,
                    'approved',0,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, region, secondProfile);
        houseId = db.queryForObject("SELECT id FROM house WHERE title='chat-house'", Long.class);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void createsPrivateRoomAndCompletesMessageReadFlow() throws Exception {
        String response = mvc.perform(post("/api/chat/rooms/create/").with(token(firstUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":" + secondProfile + ",\"house_id\":" + houseId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.other_user.username").value("landlord"))
                .andExpect(jsonPath("$.house_info.title").value("chat-house"))
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(response, "$.id")).longValue();

        mvc.perform(get("/api/chat/rooms/with-user/{id}/", secondProfile).with(token(firstUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(roomId));
        mvc.perform(get("/api/chat/rooms/{id}/", roomId).with(token(outsiderUser)))
                .andExpect(status().isNotFound());

        chatService.sendMessage(firstUser, roomId, "text", "hello");
        mvc.perform(get("/api/chat/unread-count/").with(token(secondUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(get("/api/chat/rooms/{id}/messages/", roomId).with(token(secondUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].content").value("hello"))
                .andExpect(jsonPath("$.results[0].sender_user_id").value(firstUser));
        mvc.perform(post("/api/chat/rooms/{id}/read/", roomId).with(token(secondUser)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat/unread-count/").with(token(secondUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));

        mvc.perform(post("/api/chat/rooms/{id}/share-house/", roomId).with(token(secondUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"house_id\":" + houseId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message_type").value("house_share"))
                .andExpect(jsonPath("$.content.title").value("chat-house"));
        mvc.perform(get("/api/chat/rooms/").with(token(firstUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].last_message.message_type")
                        .value("house_share"));
    }

    @Test
    void savesInquiryTextAndHouseCardInOneRoom() {
        ChatService.HouseInquiryResult result = chatService.sendHouseInquiry(
                firstUser,
                houseId,
                "  您好，我对这套房子很感兴趣，可以聊聊吗？  ");

        assertThat(result.textMessage().content())
                .isEqualTo("您好，我对这套房子很感兴趣，可以聊聊吗？");
        assertThat(result.houseShareMessage().messageType()).isEqualTo("house_share");
        assertThat(db.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE room_id=?",
                Integer.class,
                result.roomId())).isEqualTo(2);
        assertThat(db.queryForList(
                "SELECT message_type FROM chat_message WHERE room_id=? ORDER BY id",
                String.class,
                result.roomId())).containsExactly("text", "house_share");

        assertThatThrownBy(() -> chatService.sendHouseInquiry(
                secondUser,
                houseId,
                "咨询自己的房源"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自己");
    }
    private RequestPostProcessor token(Long userId) {
        return jwt().jwt(value -> value.subject(userId.toString()));
    }

    private Long user(String name, String role, String phone) {
        db.update("""
                INSERT INTO auth_user (password,is_superuser,username,first_name,last_name,email,
                    is_staff,is_active,date_joined)
                VALUES ('unused',FALSE,?,'','','',FALSE,TRUE,CURRENT_TIMESTAMP)
                """, name);
        Long id = db.queryForObject("SELECT id FROM auth_user WHERE username=?", Long.class, name);
        db.update("""
                INSERT INTO user_profile (phone,role,avatar,create_time,update_time,user_id)
                VALUES (?,?,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                """, phone, role, id);
        return id;
    }

    private Long profile(Long userId) {
        return db.queryForObject("SELECT id FROM user_profile WHERE user_id=?", Long.class, userId);
    }

    private void clean() {
        db.update("DELETE FROM chat_message");
        db.update("DELETE FROM chat_room_participants");
        db.update("DELETE FROM online_status");
        db.update("DELETE FROM chat_room");
        db.update("DELETE FROM point_purchase_record");
        db.update("DELETE FROM recommend_point");
        db.update("DELETE FROM recommend_point_account");
        db.update("DELETE FROM publish_record");
        db.update("DELETE FROM favorite");
        db.update("DELETE FROM browse_history");
        db.update("DELETE FROM house");
        db.update("DELETE FROM area");
        db.update("DELETE FROM user_profile");
        db.update("DELETE FROM auth_user");
    }
}
