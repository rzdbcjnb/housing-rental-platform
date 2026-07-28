package com.bulongyu.housing.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 用户认证配置类
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    /**
     * 根据配置创建 JWT 签名密钥。
     *
     * @param properties 配置属性
     */
    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * 创建 JWT 编码器。
     *
     * @param secretKey JWT 签名密钥
     */
    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * 创建 JWT 解码器并配置签发方和时间校验。
     *
     * @param secretKey JWT 签名密钥
     * @param properties 配置属性
     */
    @Bean
    @Primary
    JwtDecoder jwtDecoder(SecretKey secretKey, JwtProperties properties) {
        return createDecoder(secretKey, properties, null);
    }

    /**
     * 创建仅接受访问令牌的解码器，防止刷新令牌访问业务接口。
     *
     * @param secretKey JWT 签名密钥
     * @param properties 配置属性
     */
    @Bean("accessJwtDecoder")
    JwtDecoder accessJwtDecoder(SecretKey secretKey, JwtProperties properties) {
        return createDecoder(secretKey, properties, JwtService.ACCESS_TYPE);
    }

    private JwtDecoder createDecoder(SecretKey secretKey, JwtProperties properties, String tokenType) {
    /**
     * 创建带签发方、有效期和用途校验的 JWT 解码器。
     *
     * @param secretKey JWT 签名密钥
     * @param properties 配置属性
     * @param tokenType 需要限制的令牌类型；为空时不限制用途
     */
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(), new JwtIssuerValidator(properties.issuer()));
        if (tokenType != null) {
            OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> typeValidator = jwt ->
                    tokenType.equals(jwt.getClaimAsString("token_type"))
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(
                                    new OAuth2Error("invalid_token", "令牌类型无效", null));
            validator = new DelegatingOAuth2TokenValidator<>(validator, typeValidator);
        }
        decoder.setJwtValidator(validator);
        return decoder;
}
}
