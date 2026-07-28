package com.bulongyu.housing.config;

import com.bulongyu.housing.entity.AuthUser;
import com.bulongyu.housing.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 通用配置类
 */
@Configuration
public class SecurityConfig {
    /**
     * 配置 Spring Security 认证、鉴权和接口访问规则。
     *
     * @param http Spring Security HTTP 配置对象
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("accessJwtDecoder") JwtDecoder accessJwtDecoder,
            Converter<Jwt, AbstractAuthenticationToken> activeUserJwtAuthenticationConverter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(accessJwtDecoder)
                        .jwtAuthenticationConverter(activeUserJwtAuthenticationConverter)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/users/register/", "/api/users/login/",
                                "/api/users/check-unique/", "/api/token/refresh/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/areas/**").permitAll()
                        .requestMatchers((RequestMatcher) request -> "GET".equals(request.getMethod())
                                && request.getRequestURI().matches("/api/houses/(?:\\d+/)?")).permitAll()
                        .requestMatchers((RequestMatcher) request -> "POST".equals(request.getMethod())
                                && request.getRequestURI().matches("/api/houses/\\d+/click/")).permitAll()
                        .requestMatchers((RequestMatcher) request -> "GET".equals(request.getMethod())
                                && request.getRequestURI().matches("/api/houses/\\d+/recommend/"))
                        .permitAll()
                        .requestMatchers("/ws/chat/**").permitAll()
                        .requestMatchers("/ws/notifications/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    /**
     * 将 JWT 中的角色信息转换为 Spring Security 权限。
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }


    /**
     * 在构建认证信息前校验账号实时状态，禁用账号的旧令牌不能继续访问系统。
     *
     * @param userMapper 用户数据访问组件
     */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> activeUserJwtAuthenticationConverter(
            UserMapper userMapper) {
        JwtAuthenticationConverter delegate = jwtAuthenticationConverter();
        return jwt -> {
            AuthUser user = userMapper.findById(Long.valueOf(jwt.getSubject()));
            if (user == null || !user.active()) {
                throw new DisabledException("账号已禁用");
            }
            return delegate.convert(jwt);
        };
    }
    /**
     * 配置跨域请求允许的来源、方法和请求头。
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://127.0.0.1:*", "http://localhost:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
        configuration.setExposedHeaders(List.of("X-Request-ID"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

