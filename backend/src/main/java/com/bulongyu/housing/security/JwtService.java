package com.bulongyu.housing.security;

import com.bulongyu.housing.vo.TokenPair;

import com.bulongyu.housing.entity.AuthUser;
import com.bulongyu.housing.entity.UserProfile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户认证业务服务
 */
@Service
public class JwtService {
    public static final String ACCESS_TYPE = "access";
    public static final String REFRESH_TYPE = "refresh";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;

    /**
     * 初始化 {@code JwtService} 并注入所需依赖。
     *
     * @param encoder JWT 编码器
     * @param decoder JWT 解码器
     * @param properties 配置属性
     */
    public JwtService(JwtEncoder encoder, JwtDecoder decoder, JwtProperties properties) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
    }

    /**
     * 签发访问令牌与刷新令牌。
     *
     * @param user 用户
     * @param profile 用户资料
     * @return 条件成立时返回 true，否则返回 false
     */
    public TokenPair issueTokenPair(AuthUser user, UserProfile profile) {
        return new TokenPair(issue(user, profile, ACCESS_TYPE), issue(user, profile, REFRESH_TYPE));
    }

    /**
     * 解析并校验刷新令牌。
     *
     * @param token 令牌
     */
    public Jwt decodeRefreshToken(String token) {
        // 解码器负责签名、签发者和有效期校验；此处额外限制令牌用途，禁止访问令牌冒充刷新令牌。
        Jwt jwt = decoder.decode(token);
        if (!REFRESH_TYPE.equals(jwt.getClaimAsString("token_type"))) {
            throw new IllegalArgumentException("Token is not a refresh token");
        }
        return jwt;
    }

    /**
     * 判断是否sue。
     *
     * @param user 用户
     * @param profile 用户资料
     * @param tokenType 令牌类型
     * @return 条件成立时返回 true，否则返回 false
     */
    private String issue(AuthUser user, UserProfile profile, String tokenType) {
        // 访问令牌和刷新令牌共享身份声明，但使用各自有效期并通过 token_type 明确隔离用途。
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ACCESS_TYPE.equals(tokenType)
                ? properties.accessTtl() : properties.refreshTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .claim("user_id", user.id())
                .claim("username", user.username())
                .claim("role", profile.role())
                .claim("token_type", tokenType)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
