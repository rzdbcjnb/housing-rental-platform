package com.bulongyu.housing.house;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HouseControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    Long landlord, landlordProfile, tenant, admin, street;

    @BeforeEach void seed() {
        db.update("DELETE FROM house");
        db.update("DELETE FROM area");
        db.update("DELETE FROM user_profile");
        db.update("DELETE FROM auth_user");
        landlord = user("landlord", "landlord", "13800000001");
        landlordProfile = db.queryForObject(
                "SELECT id FROM user_profile WHERE user_id=?", Long.class, landlord);
        tenant = user("tenant", "tenant", "13800000002");
        admin = user("admin", "admin", "13800000003");
        Long city = area("Dalian", null, 1);
        Long district = area("Ganjingzi", city, 2);
        street = area("HighTech", district, 3);
    }

    @Test void filtersAndCompletesLifecycle() throws Exception {
        mvc.perform(get("/api/areas/").param("level", "province"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        house("matching", "approved", 1800, 2, 1);
        house("expensive", "approved", 2600, 2, 1);
        house("pending", "pending", 1500, 2, 1);
        house("no-hall", "approved", 1600, 2, 0);
        mvc.perform(get("/api/areas/").param("level", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("HighTech"));
        mvc.perform(get("/api/houses/").param("city", "Dalian")
                        .param("price_max", "2000").param("bedroom_min", "2")
                        .param("living_room_min", "1").param("bathroom_min", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].title").value("matching"));

        String body = """
                {"title":"two-bedroom","price":1900,"area":72,"bedroom_count":2,
                 "living_room_count":1,"bathroom_count":1,"kitchen_count":1,
                 "house_type":"whole","region":%d,"address_detail":"exact address"}
                """.formatted(street);
        db.update("""
                INSERT INTO publish_record (amount,is_paid,created_at,house_id,user_id)
                VALUES (10.00,TRUE,CURRENT_TIMESTAMP,NULL,?)
                """, landlordProfile);
        mvc.perform(post("/api/houses/").with(token(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        String response = mvc.perform(post("/api/houses/").with(token(landlord))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rooms").value("2\u5ba41\u53851\u536b1\u53a8"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();
        mvc.perform(get("/api/houses/{id}/", id)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/houses/{id}/", id).with(token(landlord))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"living_room_count\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms").value("2\u5ba42\u53851\u536b1\u53a8"));
        mvc.perform(put("/api/admin/houses/{id}/audit/", id).with(token(landlord))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/houses/{id}/audit/", id).with(token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/houses/{id}/", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.address_detail").value(""))
                .andExpect(jsonPath("$.landlord_info.phone").doesNotExist());
        mvc.perform(patch("/api/houses/{id}/", id).with(token(landlord))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));
        mvc.perform(get("/api/houses/{id}/", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/houses/{id}/", id).with(token(landlord)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address_detail").value("exact address"));
        mvc.perform(put("/api/admin/houses/{id}/audit/", id).with(token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/houses/{id}/", id).with(token(landlord)))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token(Long id) {
        return jwt().jwt(value -> value.subject(id.toString()));
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

    private Long area(String name, Long parent, int level) {
        db.update("INSERT INTO area (name,parent_id,level,is_active) VALUES (?,?,?,TRUE)",
                name, parent, level);
        return db.queryForObject("SELECT id FROM area WHERE name=?", Long.class, name);
    }

    private void house(String title, String status, int price, int beds, int halls) {
        db.update("""
                INSERT INTO house (title,description,price,area,rooms,bedroom_count,
                    living_room_count,bathroom_count,kitchen_count,house_type,region_id,
                    address_detail,image,landlord_id,status,click_count,is_active,
                    create_time,update_time)
                VALUES (?,'',?,70,?,?,?,1,1,'whole',?,'','',?,?,0,TRUE,
                    CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, title, price, beds + "\u5ba4" + halls + "\u53851\u536b1\u53a8",
                beds, halls, street, landlordProfile, status);
    }
}
