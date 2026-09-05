package app.authentication.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import app.authentication.auth_service.dto.UserDto;
import app.authentication.auth_service.enums.Role;
import app.authentication.auth_service.exc.WrongCredentialsException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    /**
     * Base64 of "test-secret-0123456789abcdef0123456789ab" (40 chars, 40 raw bytes).
     * getSignInKey() base64-decodes jwt.secret; HS256 needs >= 32 decoded bytes,
     * and Decoders.BASE64 is strict about padding.
     */
    private static final String BASE64_SECRET = java.util.Base64.getEncoder()
            .encodeToString("test-secret-0123456789abcdef0123456789ab".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Mock
    private CustomUserDetailsService userDetailsService;

    private JwtService jwtService;

    private UserDto userDto;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(userDetailsService);
        ReflectionTestUtils.setField(jwtService, "secretKey", BASE64_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 86_400_000L);

        userDto = new UserDto();
        userDto.setId(UUID.randomUUID());
        userDto.setUsername("johndoe");
        userDto.setEmail("johndoe@example.com");
        userDto.setPassword("secret");
        userDto.setRole(Role.USER);
    }

    private UserDetails userDetails(UserDto user) {
        return new CustomUserDetails(user);
    }

    private Claims parseAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(BASE64_SECRET)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Test
    void generateToken_returnsValidTokenWithUsernameAsSubject() {
        when(userDetailsService.loadUserByUsername("johndoe")).thenReturn(userDetails(userDto));

        String token = jwtService.generateToken("johndoe");

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.getUsernameFromToken(token)).isEqualTo("johndoe");
    }

    @Test
    void createToken_setsSubjectAndRoleAuthorityAsIssuer() {
        String token = jwtService.createToken(new HashMap<>(), userDetails(userDto));

        Claims claims = parseAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("johndoe");
        assertThat(claims.getIssuer()).isEqualTo("USER");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void createToken_includesExtraClaims() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("scope", "read:all");

        String token = jwtService.createToken(extra, userDetails(userDto));

        assertThat(parseAllClaims(token).get("scope", String.class)).isEqualTo("read:all");
    }

    @Test
    void createToken_setsExpirationFromConfiguredExpirationProperty() {
        String token = jwtService.createToken(new HashMap<>(), userDetails(userDto));

        long expectedExpiry = parseAllClaims(token).getIssuedAt().getTime() + 86_400_000L;
        assertThat(parseAllClaims(token).getExpiration().getTime()).isEqualTo(expectedExpiry);
    }

    @Test
    void adminRole_becomesIssuerOnToken() {
        userDto.setRole(Role.ADMIN);

        String token = jwtService.createToken(new HashMap<>(), userDetails(userDto));

        assertThat(parseAllClaims(token).getIssuer()).isEqualTo("ADMIN");
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.getUsernameFromToken(token)).isEqualTo("johndoe");
    }

    @Test
    void validateToken_rejectsGarbageToken() {
        assertThat(jwtService.validateToken("this.is.not.a.jwt")).isFalse();
    }

    @Test
    void validateToken_rejectsNullToken() {
        assertThat(jwtService.validateToken(null)).isFalse();
    }

    @Test
    void validateToken_rejectsEmptyToken() {
        assertThat(jwtService.validateToken("")).isFalse();
    }

    @Test
    void validateToken_rejectsTokenSignedWithDifferentKey() {
        String otherSecret = java.util.Base64.getEncoder()
                .encodeToString("another-secret-0123456789abcdef0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder()
                .setSubject("johndoe")
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(otherSecret)), io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtService.validateToken(foreignToken)).isFalse();
    }

    @Test
    void validateToken_rejectsExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expiration", -60_000L);
        String token = jwtService.createToken(new HashMap<>(), userDetails(userDto));

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void getUsernameFromToken_rejectsGarbageToken() {
        assertThatThrownBy(() -> jwtService.getUsernameFromToken("garbage.token.value"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void generateToken_propagatesUsernameNotFoundException() {
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new WrongCredentialsException("User not found"));

        assertThatThrownBy(() -> jwtService.generateToken("ghost"))
                .isInstanceOf(WrongCredentialsException.class);
    }
}
