package com.bulongyu.housing.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 兼容 Django PBKDF2-SHA256 格式的密码编码器
 */
@Component
public class DjangoPbkdf2PasswordEncoder implements PasswordEncoder {
    static final String ALGORITHM = "pbkdf2_sha256";
    static final int DEFAULT_ITERATIONS = 1_200_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final char[] SALT_CHARACTERS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final int iterations;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 兼容 Django PBKDF2-SHA256 格式的密码编码器
     */
    public DjangoPbkdf2PasswordEncoder() {
        this(DEFAULT_ITERATIONS);
    }

    /**
     * 兼容 Django PBKDF2-SHA256 格式的密码编码器
     *
     * @param iterations PBKDF2 迭代次数
     */
    DjangoPbkdf2PasswordEncoder(int iterations) {
        this.iterations = iterations;
    }

    /**
     * 兼容 Django PBKDF2-SHA256 格式的密码编码器
     *
     * @param rawPassword 待校验的原始密码
     */
    @Override
    public String encode(CharSequence rawPassword) {
        String salt = generateSalt(22);
        return encode(rawPassword, salt, iterations);
    }

    /**
     * 兼容 Django PBKDF2-SHA256 格式的密码编码器
     *
     * @param rawPassword 待校验的原始密码
     * @param encodedPassword encoded密码
     * @return 条件成立时返回 true，否则返回 false
     */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.split("\\$", 4);
        if (parts.length != 4 || !ALGORITHM.equals(parts[0])) {
            return false;
        }
        try {
            int encodedIterations = Integer.parseInt(parts[1]);
            String candidate = encode(rawPassword, parts[2], encodedIterations);
            // 使用常量时间比较降低摘要比较过程泄露时序信息的风险。
            return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8),
                    encodedPassword.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 兼容 Django PBKDF2-SHA256 格式的密码编码器
     *
     * @param encodedPassword encoded密码
     * @return 条件成立时返回 true，否则返回 false
     */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.split("\\$", 4);
        if (parts.length != 4 || !ALGORITHM.equals(parts[0])) {
            return true;
        }
        try {
            return Integer.parseInt(parts[1]) < iterations;
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    /**
     * 兼容 Django PBKDF2-SHA256 格式的密码编码器
     *
     * @param rawPassword 待校验的原始密码
     * @param salt 密码盐
     * @param iterationCount iteration数量
     */
    private String encode(CharSequence rawPassword, String salt, int iterationCount) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toString().toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8), iterationCount, KEY_LENGTH_BITS);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return ALGORITHM + "$" + iterationCount + "$" + salt + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2-SHA256 is unavailable", exception);
        }
    }

    /**
     * 兼容 Django PBKDF2-SHA256 格式的密码编码器
     *
     * @param length 盐值长度
     */
    private String generateSalt(int length) {
        StringBuilder salt = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            salt.append(SALT_CHARACTERS[secureRandom.nextInt(SALT_CHARACTERS.length)]);
        }
        return salt.toString();
    }
}
