package com.common.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordUtilTest {

    private final PasswordUtil passwordUtil = new PasswordUtil();

    @Test
    void hashPassword_returnsBcryptHashDifferentFromRaw() {
        String raw = "s3cr3t-pass";

        String hashed = passwordUtil.hashPassword(raw);

        assertThat(hashed).isNotNull();
        assertThat(hashed).isNotEqualTo(raw);
        assertThat(hashed).startsWith("$2");
    }

    @Test
    void matches_returnsTrueForOriginalRawPassword() {
        String raw = "s3cr3t-pass";
        String hashed = passwordUtil.hashPassword(raw);

        assertThat(passwordUtil.matches(raw, hashed)).isTrue();
    }

    @Test
    void matches_returnsFalseForWrongPassword() {
        String hashed = passwordUtil.hashPassword("s3cr3t-pass");

        assertThat(passwordUtil.matches("wrong-pass", hashed)).isFalse();
    }

    @Test
    void hashPassword_producesDifferentHashesForSameRaw() {
        String raw = "s3cr3t-pass";

        String first = passwordUtil.hashPassword(raw);
        String second = passwordUtil.hashPassword(raw);

        assertThat(first).isNotEqualTo(second);
        assertThat(passwordUtil.matches(raw, second)).isTrue();
    }
}
