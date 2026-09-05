package api.gateway.api_gateway.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Unit tests for {@link JwtUtil}. The util reads jwt.secret via @Value, so the
 * secret is injected with ReflectionTestUtils. JwtUtil Base64-decodes the secret
 * before building the HMAC key, therefore the secret string must itself be valid
 * Base64 of at least 32 bytes (jjwt requires a >= 256-bit key for HS256).
 */
class JwtUtilTest {

    // exactly 32 bytes -> unambiguous HS256 after Base64 decode
    private static final String RAW_SECRET = "01234567890123456789012345678901";
    private static final String BASE64_SECRET = Base64.getEncoder().encodeToString(RAW_SECRET.getBytes());

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", BASE64_SECRET);
    }

    private String tokenSignedBy(String base64Secret, String subject, Instant expiration) {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        var builder = Jwts.builder().setSubject(subject);
        if (expiration != null) {
            builder = builder.setExpiration(Date.from(expiration));
        }
        return builder.signWith(key).compact();
    }

    @Test
    void validateToken_acceptsValidTokenSignedWithSameSecret() {
        String token = tokenSignedBy(BASE64_SECRET, "john", Instant.now().plusSeconds(600));

        assertThatCode(() -> jwtUtil.validateToken(token)).doesNotThrowAnyException();
    }

    @Test
    void validateToken_acceptsTokenWithoutExpirationClaim() {
        String token = tokenSignedBy(BASE64_SECRET, "john", null);

        assertThatCode(() -> jwtUtil.validateToken(token)).doesNotThrowAnyException();
    }

    @Test
    void validateToken_rejectsGarbageString() {
        assertThatThrownBy(() -> jwtUtil.validateToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateToken_rejectsExpiredToken() {
        String expired = tokenSignedBy(BASE64_SECRET, "john", Instant.now().minusSeconds(600));

        assertThatThrownBy(() -> jwtUtil.validateToken(expired))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateToken_rejectsTokenSignedWithDifferentSecret() {
        String otherSecret = Base64.getEncoder()
                .encodeToString("another-secret-key-exactly-32-byt".getBytes());

        String forged = tokenSignedBy(otherSecret, "john", Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> jwtUtil.validateToken(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateToken_rejectsNullToken() {
        assertThatThrownBy(() -> jwtUtil.validateToken(null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateToken_rejectsEmptyToken() {
        assertThatThrownBy(() -> jwtUtil.validateToken(""))
                .isInstanceOf(RuntimeException.class);
    }
}
