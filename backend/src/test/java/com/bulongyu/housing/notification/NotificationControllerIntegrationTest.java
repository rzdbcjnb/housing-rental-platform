package com.bulongyu.housing.notification;

import com.bulongyu.housing.service.NotificationService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired NotificationService notificationService;
    Long adminUser;
    Long adminProfile;
    Long tenantUser;
    Long tenantProfile;

    @BeforeEach
    void seed() {
        clean();
        adminUser = user("admin", "admin", "13800000001");
        adminProfile = profile(adminUser);
        tenantUser = user("tenant", "tenant", "13800000002");
        tenantProfile = profile(tenantUser);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void filtersMarksAndDeletesMessages() throws Exception {
        var first = notificationService.send(tenantProfile, adminProfile, "audit",
                "audit-title", "approved", null);
        notificationService.send(tenantProfile, null, "system", "system-title", "notice", null);

        mvc.perform(get("/api/notifications/messages/").with(token(tenantUser))
                        .param("type", "audit").param("is_read", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].title").value("audit-title"));
        mvc.perform(get("/api/notifications/unread-count/").with(token(tenantUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mvc.perform(put("/api/notifications/messages/{id}/read/", first.id())
                        .with(token(tenantUser))).andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count/").with(token(tenantUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(put("/api/notifications/messages/read-all/").with(token(tenantUser)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/notifications/messages/{id}/", first.id())
                        .with(token(tenantUser))).andExpect(status().isOk());
        mvc.perform(put("/api/notifications/messages/{id}/read/", first.id())
                        .with(token(adminUser))).andExpect(status().isNotFound());
    }

    @Test
    void restrictsAnnouncementManagementAndBroadcastsSystemMessages() throws Exception {
        String request = "{\"title\":\"maintenance\",\"content\":\"tonight\",\"is_active\":true}";
        mvc.perform(post("/api/notifications/announcements/").with(token(tenantUser))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        String response = mvc.perform(post("/api/notifications/announcements/")
                        .with(token(adminUser)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author_name").value("admin"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();

        mvc.perform(get("/api/notifications/unread-count/").with(token(tenantUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(get("/api/notifications/announcements/").with(token(adminUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(put("/api/notifications/announcements/{id}/", id).with(token(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("updated"));
        mvc.perform(post("/api/notifications/announcements/batch-delete/")
                        .with(token(adminUser)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk());
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
        db.update("DELETE FROM message");
        db.update("DELETE FROM announcement");
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
