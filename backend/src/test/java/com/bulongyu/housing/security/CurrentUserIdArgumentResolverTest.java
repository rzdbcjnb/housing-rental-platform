package com.bulongyu.housing.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserIdArgumentResolverTest {
    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesJwtSubjectAsUserId() throws Exception {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject("42")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MethodParameter parameter = currentUserIdParameter("required");

        assertThat(resolver.supportsParameter(parameter)).isTrue();
        assertThat(resolver.resolveArgument(parameter, null, null, null)).isEqualTo(42L);
    }

    @Test
    void resolvesNullWhenOptionalAuthenticationIsAnonymous() throws Exception {
        MethodParameter parameter = currentUserIdParameter("optional");

        assertThat(resolver.resolveArgument(parameter, null, null, null)).isNull();
    }

    @Test
    void throwsWhenRequiredAuthenticationIsAnonymous() throws Exception {
        MethodParameter parameter = currentUserIdParameter("required");

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private MethodParameter currentUserIdParameter(String methodName) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod(methodName, Long.class);
        return new MethodParameter(method, 0);
    }

    private static class Fixture {
        void required(@CurrentUserId Long userId) {
        }

        void optional(@CurrentUserId(required = false) Long userId) {
        }
    }
}
