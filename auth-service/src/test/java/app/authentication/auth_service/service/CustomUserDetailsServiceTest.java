package app.authentication.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import app.authentication.auth_service.client.UserClient;
import app.authentication.auth_service.dto.UserDto;
import app.authentication.auth_service.enums.Role;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final String USERNAME = "johndoe";

    @Mock
    private UserClient userClient;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userClient, OpenTelemetry.noop());
    }

    private UserDto createUserDto(Role role) {
        UserDto user = new UserDto();
        user.setId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setEmail("johndoe@example.com");
        user.setPassword("secret");
        user.setRole(role);
        return user;
    }

    private ResponseEntity<UserDto> ok(UserDto body) {
        return ResponseEntity.ok(body);
    }

    @Test
    void loadUserByUsername_returnsCustomUserDetailsWhenUserFound() {
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ok(createUserDto(Role.USER)));

        UserDetails details = userDetailsService.loadUserByUsername(USERNAME);

        assertThat(details).isInstanceOf(CustomUserDetails.class);
        assertThat(details.getUsername()).isEqualTo(USERNAME);
        assertThat(details.getPassword()).isEqualTo("secret");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("USER");
    }

    @Test
    void loadUserByUsername_wrapsRoleAsAuthority() {
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ok(createUserDto(Role.ADMIN)));

        UserDetails details = userDetailsService.loadUserByUsername(USERNAME);

        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ADMIN");
    }

    @Test
    void loadUserByUsername_throwsWhenUserNotFound() {
        when(userClient.getUserByUsername("ghost")).thenReturn(ResponseEntity.ok().build());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void loadUserByUsername_wrapsFeignFailureInRuntimeException() {
        when(userClient.getUserByUsername(USERNAME)).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(USERNAME))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authentication error")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
