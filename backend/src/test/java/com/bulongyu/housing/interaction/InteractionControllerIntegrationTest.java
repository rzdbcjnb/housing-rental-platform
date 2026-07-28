package com.bulongyu.housing.interaction;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InteractionControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    Long userId;
    Long profileId;
    Long houseId;

    @BeforeEach
    void seed() {
        clean();
        db.update("""
                INSERT INTO auth_user (password,is_superuser,username,first_name,last_name,email,
                    is_staff,is_active,date_joined)
                VALUES ('unused',FALSE,'tenant','','','',FALSE,TRUE,CURRENT_TIMESTAMP)
                """);
        userId = db.queryForObject("SELECT id FROM auth_user WHERE username='tenant'", Long.class);
        db.update("""
                INSERT INTO user_profile (phone,role,avatar,create_time,update_time,user_id)
                VALUES ('13800000000','tenant','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                """, userId);
        profileId = db.queryForObject("SELECT id FROM user_profile WHERE user_id=?", Long.class, userId);
        db.update("INSERT INTO area (name,parent_id,level,is_active) VALUES ('Dalian',NULL,1,TRUE)");
        Long areaId = db.queryForObject("SELECT id FROM area WHERE name='Dalian'", Long.class);
        db.update("""
                INSERT INTO house (title,description,price,area,rooms,bedroom_count,
                    living_room_count,bathroom_count,kitchen_count,house_type,region_id,
                    address_detail,image,landlord_id,status,click_count,is_active,
                    create_time,update_time)
                VALUES ('public-house','',1800,70,'2\u5ba41\u53851\u536b1\u53a8',2,1,1,1,
                    'whole',?,'','',?,'approved',0,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, areaId, profileId);
        houseId = db.queryForObject("SELECT id FROM house WHERE title='public-house'", Long.class);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void completesFavoriteAndHistoryFlows() throws Exception {
        String body = "{\"house\":" + houseId + "}";
        String favoriteResponse = mvc.perform(post("/api/houses/favorites/add/")
                        .with(jwt().jwt(value -> value.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long favoriteId = ((Number) JsonPath.read(favoriteResponse, "$.id")).longValue();

        mvc.perform(post("/api/houses/favorites/add/")
                        .with(jwt().jwt(value -> value.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/houses/{id}/is_favorited/", houseId)
                        .with(jwt().jwt(value -> value.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_favorited").value(true))
                .andExpect(jsonPath("$.favorite_id").value(favoriteId));
        mvc.perform(get("/api/houses/favorites/")
                        .with(jwt().jwt(value -> value.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].house.title").value("public-house"));
        mvc.perform(delete("/api/houses/favorites/{id}/remove/", favoriteId)
                        .with(jwt().jwt(value -> value.subject(userId.toString()))))
                .andExpect(status().isOk());

        String historyResponse = mvc.perform(post("/api/houses/browse-history/add/")
                        .with(jwt().jwt(value -> value.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long historyId = ((Number) JsonPath.read(historyResponse, "$.id")).longValue();
        mvc.perform(post("/api/houses/browse-history/add/")
                        .with(jwt().jwt(value -> value.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(historyId));
        mvc.perform(get("/api/houses/browse-history/")
                        .with(jwt().jwt(value -> value.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(delete("/api/houses/browse-history/clear/")
                        .with(jwt().jwt(value -> value.subject(userId.toString()))))
                .andExpect(status().isOk());
    }

    private void clean() {
        db.update("DELETE FROM message");
        db.update("DELETE FROM announcement");
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
