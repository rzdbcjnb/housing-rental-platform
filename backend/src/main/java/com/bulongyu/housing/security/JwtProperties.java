package com.bulongyu.housing.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 用户认证配置属性
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {
    /**
     * 初始化认证令牌Properties并注入所需依赖。
     *
     * @param secret JWT 签名密钥
     * @param issuer JWT 签发者
     * @param accessTtl 访问令牌有效期
     * @param refreshTtl 刷新令牌有效期
     */
    public JwtProperties {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
    }
}
