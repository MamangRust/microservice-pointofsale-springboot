package app.authentication.auth_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import app.authentication.auth_service.dto.AuthRequest;
import app.authentication.auth_service.dto.AuthResponse;
import app.authentication.auth_service.dto.UserDto;
import app.authentication.auth_service.enums.Role;
import app.authentication.auth_service.exc.GeneralExceptionHandler;
import app.authentication.auth_service.exc.WrongCredentialsException;
import app.authentication.auth_service.service.AuthService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private UserDto createUserDto() {
        UserDto user = new UserDto();
        user.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        user.setUsername("johndoe");
        user.setEmail("johndoe@example.com");
        user.setPassword("secret");
        user.setRole(Role.USER);
        return user;
    }

    @Test
    void register_returnsUserDtoWith200() throws Exception {
        when(authService.register(any(AuthRequest.class))).thenReturn(createUserDto());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"johndoe\",\"password\":\"secret\",\"email\":\"johndoe@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("11111111-2222-3333-4444-555555555555"))
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.email").value("johndoe@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        ArgumentCaptor<AuthRequest> captor = ArgumentCaptor.forClass(AuthRequest.class);
        verify(authService).register(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("johndoe");
        assertThat(captor.getValue().getPassword()).isEqualTo("secret");
    }

    @Test
    void login_returnsTokenWith200() throws Exception {
        when(authService.login(any(AuthRequest.class)))
                .thenReturn(AuthResponse.builder().token("jwt-token-abc").build());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"johndoe\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-abc"));
    }

    @Test
    void login_wrongCredentials_returns401ViaAdvice() throws Exception {
        when(authService.login(any(AuthRequest.class)))
                .thenThrow(new WrongCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"johndoe\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void login_genericFailure_returns400ViaAdvice() throws Exception {
        when(authService.login(any(AuthRequest.class)))
                .thenThrow(new RuntimeException("user service down"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"johndoe\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("user service down"));
    }

    @Test
    void register_failure_returns400ViaAdvice() throws Exception {
        when(authService.register(any(AuthRequest.class)))
                .thenThrow(new RuntimeException("username already exists"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"johndoe\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("username already exists"));
    }
}
