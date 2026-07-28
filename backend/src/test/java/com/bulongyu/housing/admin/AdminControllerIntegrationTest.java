package com.bulongyu.housing.admin;

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

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    final List<Long> userIds = new ArrayList<>();
    Long admin;
    Long tenant;
    Long landlordProfile;

    @BeforeEach
    void seed() {
        admin = user("admin-api-admin", "admin", "13900000101");
        tenant = user("admin-api-tenant", "tenant", "13900000102");
        Long landlord = user("admin-api-landlord", "landlord", "13900000103");
        landlordProfile = db.queryForObject("SELECT id FROM user_profile WHERE user_id=?", Long.class, landlord);
        db.update("INSERT INTO area(name,parent_id,level,is_active) VALUES ('测试街道',NULL,3,TRUE)");
        Long area = db.queryForObject("SELECT id FROM area WHERE name='测试街道'", Long.class);
        db.update("""
                INSERT INTO house(title,description,price,area,rooms,bedroom_count,living_room_count,
                  bathroom_count,kitchen_count,house_type,region_id,address_detail,image,landlord_id,
                  status,click_count,is_active,create_time,update_time)
                VALUES('待审房源','',1800,70,'2室1厅1卫1厨',2,1,1,1,'whole',?,'','',?,
                  'pending',0,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, area, landlordProfile);
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM house WHERE landlord_id=?", landlordProfile);
        db.update("DELETE FROM area WHERE name='测试街道'");
        for (Long userId : userIds) db.update("DELETE FROM user_profile WHERE user_id=?", userId);
        for (Long userId : userIds) db.update("DELETE FROM auth_user WHERE id=?", userId);
        userIds.clear();
    }

    @Test
    void managesUsersListsHousesAndBuildsDashboard() throws Exception {
        mvc.perform(get("/api/admin/dashboard/").with(token(tenant))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard/").with(token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houses.pending").value(1))
                .andExpect(jsonPath("$.users.admin").value(1))
                .andExpect(jsonPath("$.price_distribution.length()").value(6))
                .andExpect(jsonPath("$.recent_trend.length()").value(7));

        String created = mvc.perform(post("/api/admin/users/").with(token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"managed-user\",\"password\":\"secret123\",\"phone\":\"13900000104\",\"role\":\"tenant\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("managed-user"))
                .andReturn().getResponse().getContentAsString();
        long managed = ((Number) JsonPath.read(created, "$.id")).longValue();
        userIds.add(managed);
        mvc.perform(put("/api/admin/users/{id}/status/", managed).with(token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"is_active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.is_active").value(false));
        mvc.perform(get("/api/admin/users/").with(token(admin)).param("keyword", "managed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(get("/api/admin/houses/").with(token(admin)).param("status", "pending"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].landlord_username").value("admin-api-landlord"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token(Long id) {
        return jwt().jwt(value -> value.subject(id.toString()));
    }

    private Long user(String username, String role, String phone) {
        db.update("""
                INSERT INTO auth_user(password,is_superuser,username,first_name,last_name,email,
                  is_staff,is_active,date_joined) VALUES('unused',FALSE,?,'','','',FALSE,TRUE,CURRENT_TIMESTAMP)
                """, username);
        Long id = db.queryForObject("SELECT id FROM auth_user WHERE username=?", Long.class, username);
        db.update("INSERT INTO user_profile(phone,role,avatar,create_time,update_time,user_id) VALUES(?,?,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
                phone, role, id);
        userIds.add(id);
        return id;
    }
}
