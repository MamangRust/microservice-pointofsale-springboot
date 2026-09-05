package app.authentication.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import app.authentication.auth_service.dto.UserDto;
import app.authentication.auth_service.enums.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsTest {

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        UserDto user = new UserDto();
        user.setId(java.util.UUID.randomUUID());
        user.setUsername("janedoe");
        user.setEmail("janedoe@example.com");
        user.setPassword("hashed-password");
        user.setRole(Role.USER);
        userDetails = new CustomUserDetails(user);
    }

    @Test
    void getUsername_returnsUsernameFromUserDto() {
        assertThat(userDetails.getUsername()).isEqualTo("janedoe");
    }

    @Test
    void getPassword_returnsPasswordFromUserDto() {
        assertThat(userDetails.getPassword()).isEqualTo("hashed-password");
    }

    @Test
    void getAuthorities_exposesRoleNameAsSingleAuthority() {
        assertThat(userDetails.getAuthorities())
                .hasSize(1)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("USER");
    }

    @Test
    void adminRole_mapsToAdminAuthority() {
        UserDto admin = new UserDto();
        admin.setId(java.util.UUID.randomUUID());
        admin.setUsername("root");
        admin.setEmail("root@example.com");
        admin.setPassword("hashed-password");
        admin.setRole(Role.ADMIN);

        CustomUserDetails adminDetails = new CustomUserDetails(admin);

        assertThat(adminDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ADMIN");
    }

    @Test
    void accountFlags_areAlwaysTrue() {
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void getUser_exposesUnderlyingUserDto() {
        assertThat(userDetails.getUser().getEmail()).isEqualTo("janedoe@example.com");
    }

    @Test
    void wrappedDetails_canRoundtripThroughJjwtClaims() {
        // Mirrors JwtService.createToken: subject = username, issuer = role authority.
        // Asserts CustomUserDetails integrates cleanly with the JWT pipeline.
        Map<String, Object> claims = new HashMap<>();
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuer(userDetails.getAuthorities().iterator().next().getAuthority())
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(Base64Secrets.SECRET)), io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        Claims parsed = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(Base64Secrets.SECRET)))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(parsed.getSubject()).isEqualTo("janedoe");
        assertThat(parsed.getIssuer()).isEqualTo("USER");
    }

    /** Shared test secret: base64 of 32+ raw bytes ("test-secret-0123456789abcdef0123456789ab"). */
    static final class Base64Secrets {
        private Base64Secrets() {
        }

        static final String SECRET = java.util.Base64.getEncoder()
                .encodeToString("test-secret-0123456789abcdef0123456789ab".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
