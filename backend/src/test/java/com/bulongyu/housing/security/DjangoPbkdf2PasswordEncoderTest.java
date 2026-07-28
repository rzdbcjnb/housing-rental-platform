package com.bulongyu.housing.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DjangoPbkdf2PasswordEncoderTest {
    private static final String DJANGO_VECTOR =
            "pbkdf2_sha256$1200000$testsalt$Dhr87O/xq0ZWTXsqb6n091Ee6iZNSAbmeAou8iOSoBo=";

    @Test
    void verifiesPasswordHashGeneratedByDjango() {
        DjangoPbkdf2PasswordEncoder encoder = new DjangoPbkdf2PasswordEncoder();
        assertThat(encoder.matches("correct-horse", DJANGO_VECTOR)).isTrue();
        assertThat(encoder.matches("wrong-password", DJANGO_VECTOR)).isFalse();
    }

    @Test
    void encodesNewPasswordsInDjangoCompatibleFormat() {
        DjangoPbkdf2PasswordEncoder encoder = new DjangoPbkdf2PasswordEncoder(1_000);
        String encoded = encoder.encode("new-password");
        assertThat(encoded).startsWith("pbkdf2_sha256$1000$");
        assertThat(encoder.matches("new-password", encoded)).isTrue();
    }

    @Test
    void rejectsUnsupportedOrMalformedHashes() {
        DjangoPbkdf2PasswordEncoder encoder = new DjangoPbkdf2PasswordEncoder();
        assertThat(encoder.matches("password", "bcrypt$bad")).isFalse();
        assertThat(encoder.matches("password", "pbkdf2_sha256$bad$salt$value")).isFalse();
    }
}
