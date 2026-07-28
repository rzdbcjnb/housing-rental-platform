package com.bulongyu.housing.chat;

import com.bulongyu.housing.service.ChatService;
import com.bulongyu.housing.websocket.ChatHandshakeInterceptor;
import com.bulongyu.housing.entity.AuthUser;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import com.bulongyu.housing.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHandler;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
class ChatHandshakeInterceptorTest {
    @Autowired ChatHandshakeInterceptor interceptor;
    @Autowired ChatService chatService;
    @Autowired JwtService jwtService;
    @Autowired UserMapper userMapper;
    @Autowired JdbcTemplate db;
    Long participantUser;
    Long participantProfile;
    Long otherProfile;
    Long outsiderUser;

    @BeforeEach
    void seed() {
        clean();
        participantUser = user("participant", "13800000001");
        participantProfile = userMapper.findProfileByUserId(participantUser).id();
        Long otherUser = user("other", "13800000002");
        otherProfile = userMapper.findProfileByUserId(otherUser).id();
        outsiderUser = user("outsider", "13800000003");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void acceptsValidParticipantTokenAndRejectsOutsider() {
        Long roomId = chatService.getOrCreate(participantUser, otherProfile, null).id();
        var attributes = new HashMap<String, Object>();
        boolean accepted = interceptor.beforeHandshake(
                request(roomId, accessToken(participantUser, "participant")), response(),
                mock(WebSocketHandler.class), attributes);
        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(ChatHandshakeInterceptor.USER_ID, participantUser)
                .containsEntry(ChatHandshakeInterceptor.ROOM_ID, roomId);

        boolean rejected = interceptor.beforeHandshake(
                request(roomId, accessToken(outsiderUser, "outsider")), response(),
                mock(WebSocketHandler.class), new HashMap<>());
        assertThat(rejected).isFalse();
    }

    private ServletServerHttpRequest request(Long roomId, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/chat/" + roomId + "/");
        request.setQueryString("token=" + token);
        return new ServletServerHttpRequest(request);
    }

    private ServletServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }

    private String accessToken(Long userId, String username) {
        AuthUser user = new AuthUser(userId, username, "", true, false, false,
                null, LocalDateTime.now());
        UserProfile profile = userMapper.findProfileByUserId(userId);
        return jwtService.issueTokenPair(user, profile).access();
    }

    private Long user(String username, String phone) {
        db.update("""
                INSERT INTO auth_user (password,is_superuser,username,first_name,last_name,email,
                    is_staff,is_active,date_joined)
                VALUES ('unused',FALSE,?,'','','',FALSE,TRUE,CURRENT_TIMESTAMP)
                """, username);
        Long id = db.queryForObject("SELECT id FROM auth_user WHERE username=?", Long.class, username);
        db.update("""
                INSERT INTO user_profile (phone,role,avatar,create_time,update_time,user_id)
                VALUES (?,'tenant','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                """, phone, id);
        return id;
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
