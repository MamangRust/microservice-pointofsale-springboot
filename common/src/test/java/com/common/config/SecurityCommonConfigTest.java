package com.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Unit test for the shared security config. Only {@code passwordEncoder()} is
 * tested directly — {@code securityFilterChain()} needs a built HttpSecurity
 * and is left to integration coverage.
 */
class SecurityCommonConfigTest {

    private final SecurityCommonConfig config = new SecurityCommonConfig();

    @Test
    void passwordEncoder_isBcryptPasswordEncoder() {
        assertThat(config.passwordEncoder()).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    void passwordEncoder_encodesAndVerifiesPasswords() {
        var encoder = config.passwordEncoder();

        String hashed = encoder.encode("s3cr3t-pass");

        assertThat(hashed).startsWith("$2");
        assertThat(encoder.matches("s3cr3t-pass", hashed)).isTrue();
        assertThat(encoder.matches("wrong-pass", hashed)).isFalse();
    }
}
