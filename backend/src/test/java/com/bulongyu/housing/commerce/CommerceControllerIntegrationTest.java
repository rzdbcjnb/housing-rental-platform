package com.bulongyu.housing.commerce;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommerceControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    Long userId;
    Long profileId;
    Long regionId;
    Long houseId;

    @BeforeEach
    void seed() {
        clean();
        db.update("""
                INSERT INTO auth_user (password,is_superuser,username,first_name,last_name,email,
                    is_staff,is_active,date_joined)
                VALUES ('unused',FALSE,'landlord','','','',FALSE,TRUE,CURRENT_TIMESTAMP)
                """);
        userId = db.queryForObject("SELECT id FROM auth_user WHERE username='landlord'", Long.class);
        db.update("""
                INSERT INTO user_profile (phone,role,avatar,create_time,update_time,user_id)
                VALUES ('13800000000','landlord','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                """, userId);
        profileId = db.queryForObject("SELECT id FROM user_profile WHERE user_id=?", Long.class, userId);
        db.update("INSERT INTO area (name,parent_id,level,is_active) VALUES ('Dalian',NULL,1,TRUE)");
        regionId = db.queryForObject("SELECT id FROM area WHERE name='Dalian'", Long.class);
        house("house-1");
        house("house-2");
        house("house-3");
        houseId = db.queryForObject("SELECT id FROM house WHERE title='house-1'", Long.class);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void consumesOnePaymentPerPaidPublish() throws Exception {
        mvc.perform(get("/api/houses/publish-limit/").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.need_pay").value(true))
                .andExpect(jsonPath("$.total_published").value(3));
        mvc.perform(post("/api/houses/").with(token())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("blocked")))
                .andExpect(status().isPaymentRequired());
        mvc.perform(post("/api/houses/simulate-payment/").with(token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record_id").isNumber());
        mvc.perform(post("/api/houses/").with(token())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("paid-house")))
                .andExpect(status().isCreated());
        Integer linked = db.queryForObject(
                "SELECT COUNT(*) FROM publish_record WHERE house_id IS NOT NULL", Integer.class);
        assertThat(linked).isEqualTo(1);
        mvc.perform(post("/api/houses/").with(token())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("reuse-blocked")))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void rechargesInvestsBuysAndRecordsClicks() throws Exception {
        mvc.perform(get("/api/houses/account-balance/").with(token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
        mvc.perform(post("/api/houses/recharge-points/").with(token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(100));
        mvc.perform(post("/api/houses/invest-points/").with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"house_id\":" + houseId + ",\"points\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60))
                .andExpect(jsonPath("$.house_points").value(40));
        mvc.perform(post("/api/houses/buy-points/").with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"house_id\":" + houseId + ",\"points\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points").value(60));
        mvc.perform(post("/api/houses/invest-points/").with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"house_id\":" + houseId + ",\"points\":50}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/houses/recommend-status/").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houses[0].max_points").value(100));
        mvc.perform(post("/api/houses/{id}/click/", houseId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.click_count").value(1));
    }

    private RequestPostProcessor token() {
        return jwt().jwt(value -> value.subject(userId.toString()));
    }

    private String createBody(String title) {
        return """
                {"title":"%s","price":1800,"area":70,"bedroom_count":2,
                 "living_room_count":1,"bathroom_count":1,"kitchen_count":1,
                 "house_type":"whole","region":%d}
                """.formatted(title, regionId);
    }

    private void house(String title) {
        db.update("""
                INSERT INTO house (title,description,price,area,rooms,bedroom_count,
                    living_room_count,bathroom_count,kitchen_count,house_type,region_id,
                    address_detail,image,landlord_id,status,click_count,is_active,
                    create_time,update_time)
                VALUES (?,'',1800,70,'2\u5ba41\u53851\u536b1\u53a8',2,1,1,1,'whole',?,'','',?,
                    'approved',0,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, title, regionId, profileId);
    }

    private void clean() {
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
