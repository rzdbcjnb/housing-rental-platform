package com.bulongyu.housing.security;

import com.bulongyu.housing.vo.TokenPair;

import com.bulongyu.housing.entity.AuthUser;
import com.bulongyu.housing.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private final JwtProperties properties = new JwtProperties(
            "test-secret-that-is-at-least-thirty-two-bytes-long", "test-issuer",
            Duration.ofMinutes(5), Duration.ofDays(7));
    private final JwtConfig config = new JwtConfig();
    private final JwtService service;

    JwtServiceTest() {
        var key = config.jwtSecretKey(properties);
        service = new JwtService(config.jwtEncoder(key), config.jwtDecoder(key, properties), properties);
    }

    @Test
    void issuesAccessAndRefreshTokensWithCompatibleClaims() {
        AuthUser user = new AuthUser(7L, "alice", "hash", true, false, false,
                null, LocalDateTime.now());
        UserProfile profile = new UserProfile(3L, 7L, "13800000000", "tenant", "",
                LocalDateTime.now(), LocalDateTime.now());

        TokenPair pair = service.issueTokenPair(user, profile);
        Jwt refresh = service.decodeRefreshToken(pair.refresh());

        assertThat(pair.access()).isNotBlank();
        assertThat(refresh.getSubject()).isEqualTo("7");
        assertThat(refresh.getClaimAsString("role")).isEqualTo("tenant");
        assertThat(refresh.getClaimAsString("token_type")).isEqualTo("refresh");
    }

    @Test
    void rejectsAccessTokenAsRefreshToken() {
        AuthUser user = new AuthUser(7L, "alice", "hash", true, false, false,
                null, LocalDateTime.now());
        UserProfile profile = new UserProfile(3L, 7L, "13800000000", "tenant", "",
                LocalDateTime.now(), LocalDateTime.now());
        TokenPair pair = service.issueTokenPair(user, profile);

        assertThatThrownBy(() -> service.decodeRefreshToken(pair.access()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
