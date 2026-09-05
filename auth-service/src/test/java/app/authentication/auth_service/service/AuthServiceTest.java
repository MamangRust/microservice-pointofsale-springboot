package app.authentication.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import app.authentication.auth_service.client.UserClient;
import app.authentication.auth_service.dto.AuthRequest;
import app.authentication.auth_service.dto.AuthResponse;
import app.authentication.auth_service.dto.UserDto;
import app.authentication.auth_service.enums.Role;
import app.authentication.auth_service.exc.WrongCredentialsException;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USERNAME = "johndoe";
    private static final String PASSWORD = "secret";

    @Mock
    private UserClient userClient;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private AuthRequest request;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userClient, authenticationManager, jwtService, OpenTelemetry.noop());
        request = authRequest(USERNAME, PASSWORD);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AuthRequest authRequest(String username, String password) {
        // AuthRequest exposes getters only (no setters) — populate via reflection.
        AuthRequest req = new AuthRequest();
        ReflectionTestUtils.setField(req, "username", username);
        ReflectionTestUtils.setField(req, "password", password);
        ReflectionTestUtils.setField(req, "email", username + "@example.com");
        return req;
    }

    private UserDto createUserDto() {
        UserDto user = new UserDto();
        user.setId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setEmail("johndoe@example.com");
        user.setPassword(PASSWORD);
        user.setRole(Role.USER);
        return user;
    }

    private Authentication authenticatedToken() {
        // 3-arg constructor marks the token as authenticated (isAuthenticated() == true).
        return new UsernamePasswordAuthenticationToken(USERNAME, PASSWORD, java.util.List.of());
    }

    @Test
    void login_returnsTokenWhenAuthenticationSucceeds() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authenticatedToken());
        when(jwtService.generateToken(USERNAME)).thenReturn("jwt-token-abc");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token-abc");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtService).generateToken(USERNAME);
    }

    @Test
    void login_authenticatesWithRequestUsernameAndPassword() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authenticatedToken());
        when(jwtService.generateToken(USERNAME)).thenReturn("t");

        authService.login(request);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo(USERNAME);
        assertThat(captor.getValue().getCredentials()).isEqualTo(PASSWORD);
    }

    @Test
    void login_storesAuthenticationInSecurityContext() {
        Authentication authentication = authenticatedToken();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtService.generateToken(USERNAME)).thenReturn("t");

        authService.login(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
    }

    @Test
    void login_rethrowsBadCredentialsExceptionWithoutGeneratingToken() {
        // Quirk: AuthService rethrows the raw AuthenticationException — no WrongCredentialsException mapping.
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Bad credentials");

        verify(jwtService, org.mockito.Mockito.never()).generateToken(any());
    }

    @Test
    void login_throwsWrongCredentialsExceptionWhenAuthenticationNotAuthenticated() {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(WrongCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void register_returnsUserFromUserClient() {
        UserDto user = createUserDto();
        when(userClient.createUser(request)).thenReturn(ResponseEntity.ok(user));

        UserDto result = authService.register(request);

        assertThat(result).isSameAs(user);
        assertThat(result.getUsername()).isEqualTo(USERNAME);
        verify(userClient).createUser(request);
    }

    @Test
    void register_sendsRequestAsIsToUserClient() {
        UserDto user = createUserDto();
        when(userClient.createUser(request)).thenReturn(ResponseEntity.ok(user));

        authService.register(request);

        verify(userClient).createUser(eq(request));
    }

    @Test
    void register_throwsWhenUserClientReturnsNullBody() {
        when(userClient.createUser(request)).thenReturn(ResponseEntity.ok().build());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to register user");
    }

    @Test
    void register_propagatesClientFailure() {
        when(userClient.createUser(request)).thenThrow(new RuntimeException("user-service down"));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("user-service down");
    }
}
