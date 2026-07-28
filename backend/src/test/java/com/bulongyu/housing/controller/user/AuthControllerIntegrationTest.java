package com.bulongyu.housing.controller.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {
    private static final String DJANGO_HASH =
            "pbkdf2_sha256$1200000$testsalt$Dhr87O/xq0ZWTXsqb6n091Ee6iZNSAbmeAou8iOSoBo=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM user_profile");
        jdbcTemplate.update("DELETE FROM auth_user");
    }

    @Test
    void completesRegistrationLoginProfileUpdateAndRefreshFlow() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/users/register/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"correct-horse",
                                 "phone":"13800000000","role":"tenant"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.data.user.username").value("alice"))
                .andExpect(jsonPath("$.data.user.role").value("tenant"))
                .andReturn();
        String registrationBody = registration.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(registrationBody, "$.data.tokens.access")).isNotBlank();
        String storedPassword = jdbcTemplate.queryForObject(
                "SELECT password FROM auth_user WHERE username = 'alice'", String.class);
        assertThat(storedPassword).startsWith("pbkdf2_sha256$1200000$");

        MvcResult login = mockMvc.perform(post("/api/users/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andReturn();
        String loginBody = login.getResponse().getContentAsString();
        String access = JsonPath.read(loginBody, "$.data.tokens.access");
        String refresh = JsonPath.read(loginBody, "$.data.tokens.refresh");
        mockMvc.perform(get("/api/users/info/").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());


        mockMvc.perform(get("/api/users/info/").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.phone").value("13800000000"));

        mockMvc.perform(put("/api/users/info/")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice-new","phone":"13900000000",
                                 "avatar":"https://example.com/avatar.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("更新成功"))
                .andExpect(jsonPath("$.data.username").value("alice-new"));

        mockMvc.perform(get("/api/users/check-unique/")
                        .param("field", "username").param("value", "alice-new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));

        mockMvc.perform(post("/api/token/refresh/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").isNotEmpty());

        jdbcTemplate.update("UPDATE auth_user SET is_active = FALSE WHERE username = 'alice-new'");
        mockMvc.perform(get("/api/users/info/").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatesAnExistingDjangoPasswordHashAndRejectsWrongPassword() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO auth_user
                    (password, is_superuser, username, first_name, last_name, email,
                     is_staff, is_active, date_joined)
                VALUES (?, FALSE, 'legacy', '', '', '', FALSE, TRUE, CURRENT_TIMESTAMP)
                """, DJANGO_HASH);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_user WHERE username = 'legacy'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO user_profile
                    (phone, role, avatar, create_time, update_time, user_id)
                VALUES ('13700000000', 'landlord', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """, userId);

        mockMvc.perform(post("/api/users/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"legacy\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("landlord"));

        mockMvc.perform(post("/api/users/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"legacy\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("用户名或密码错误"));
    }

    @Test
    void rejectsAdministrativeSelfRegistration() throws Exception {
        mockMvc.perform(post("/api/users/register/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin-user","password":"password",
                                 "phone":"13600000000","role":"admin"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_user", Integer.class);
        assertThat(count).isZero();
    }
}
