package com.bulongyu.housing.recommendation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    Long tenantUser, tenantProfile, landlordUser, landlordProfile, regionA, regionB, target, sameRegion;

    @BeforeEach
    void seed() {
        landlordUser = user("rec-landlord", "landlord", "13700000101");
        landlordProfile = profile(landlordUser);
        tenantUser = user("rec-tenant", "tenant", "13700000102");
        tenantProfile = profile(tenantUser);
        regionA = area("推荐区域A"); regionB = area("推荐区域B");
        target = house("目标两室", 2000, 75, 2, regionA);
        sameRegion = house("同区相似", 2100, 78, 2, regionA);
        house("异区大房", 5000, 180, 4, regionB);
        db.update("INSERT INTO favorite(create_time,house_id,user_id) VALUES(CURRENT_TIMESTAMP,?,?)", target, tenantProfile);
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM favorite WHERE user_id=?", tenantProfile);
        db.update("DELETE FROM house WHERE landlord_id=?", landlordProfile);
        db.update("DELETE FROM area WHERE name LIKE '推荐区域%'");
        db.update("DELETE FROM user_profile WHERE user_id IN (?,?)", tenantUser, landlordUser);
        db.update("DELETE FROM auth_user WHERE id IN (?,?)", tenantUser, landlordUser);
    }

    @Test
    void ranksSimilarAndUserPreferenceWithoutRequiringVectorStore() throws Exception {
        mvc.perform(get("/api/houses/{id}/recommend/", target))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("同区相似"));
        mvc.perform(get("/api/houses/user-recommend/").param("limit", "5"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/houses/user-recommend/").param("limit", "5")
                        .with(jwt().jwt(j -> j.subject(tenantUser.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sameRegion));
    }

    private Long user(String name, String role, String phone) {
        db.update("INSERT INTO auth_user(password,is_superuser,username,first_name,last_name,email,is_staff,is_active,date_joined) VALUES('unused',FALSE,?,'','','',FALSE,TRUE,CURRENT_TIMESTAMP)", name);
        Long id = db.queryForObject("SELECT id FROM auth_user WHERE username=?", Long.class, name);
        db.update("INSERT INTO user_profile(phone,role,avatar,create_time,update_time,user_id) VALUES(?,?,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)", phone, role, id);
        return id;
    }
    private Long profile(Long user) { return db.queryForObject("SELECT id FROM user_profile WHERE user_id=?", Long.class, user); }
    private Long area(String name) {
        db.update("INSERT INTO area(name,parent_id,level,is_active) VALUES(?,NULL,3,TRUE)", name);
        return db.queryForObject("SELECT id FROM area WHERE name=?", Long.class, name);
    }
    private Long house(String title, int price, int area, int bedrooms, Long region) {
        db.update("""
                INSERT INTO house(title,description,price,area,rooms,bedroom_count,living_room_count,
                  bathroom_count,kitchen_count,house_type,region_id,address_detail,image,landlord_id,
                  status,click_count,is_active,create_time,update_time)
                VALUES(?,'近地铁',?,?,?, ?,1,1,1,'whole',?,'','',?,'approved',0,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, title, price, area, bedrooms + "室1厅1卫1厨", bedrooms, region, landlordProfile);
        return db.queryForObject("SELECT id FROM house WHERE title=?", Long.class, title);
    }
}
