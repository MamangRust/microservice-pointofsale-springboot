package com.common.seed;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing helper for the seeder.
 *
 * Uses BCrypt to stay compatible with auth-service / user-service which rely on
 * BCryptPasswordEncoder (see common SecurityCommonConfig). Mirrors the role of
 * Quarkus PasswordUtil (PBKDF2) but adapted to the Spring Security encoder used here.
 */
public class PasswordUtil {

    private final PasswordEncoder encoder;

    public PasswordUtil() {
        this.encoder = new BCryptPasswordEncoder();
    }

    public String hashPassword(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hashed) {
        return encoder.matches(raw, hashed);
    }
}