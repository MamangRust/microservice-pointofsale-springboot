package com.user.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.user.user.dto.UserMapper;
import com.user.user.dto.UserMapperImpl;
import com.user.user.dto.UserRequest;
import com.user.user.entity.User;
import com.user.user.enums.Role;
import com.user.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    private final UserMapper userMapper = new UserMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService, userMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private User createUser(UUID id, String username, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("encoded-pw");
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private UserRequest createRequest(String username, String password, String email) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setEmail(email);
        return request;
    }

    @Test
    void getAllUsers_returnsMappedList() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(
                createUser(UUID.randomUUID(), "alice", "alice@example.com", Role.USER),
                createUser(UUID.randomUUID(), "bob", "bob@example.com", Role.ADMIN)));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].username").value("bob"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"));
    }

    @Test
    void getAllUsers_returnsEmptyListWhenNone() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createUser_returnsResponse() throws Exception {
        UserRequest request = createRequest("newuser", "secret", "newuser@example.com");
        when(userService.createUser(any(UserRequest.class)))
                .thenReturn(createUser(UUID.randomUUID(), "newuser", "newuser@example.com", Role.USER));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(userService).createUser(any(UserRequest.class));
    }

    @Test
    void createUser_returns409WhenUserAlreadyExists() throws Exception {
        UserRequest request = createRequest("taken", "secret", "taken@example.com");
        when(userService.createUser(any(UserRequest.class)))
                .thenThrow(new Exception("User already exists"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("User already exists"));
    }

    @Test
    void createUser_returns500OnOtherFailure() throws Exception {
        UserRequest request = createRequest("newuser", "secret", "newuser@example.com");
        when(userService.createUser(any(UserRequest.class)))
                .thenThrow(new Exception("db down"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("Error creating user"));
    }

    @Test
    void getUserById_returnsResponse() throws Exception {
        when(userService.getUserById(1L))
                .thenReturn(createUser(UUID.randomUUID(), "alice", "alice@example.com", Role.USER));

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void getUserById_returns404WhenNotFound() throws Exception {
        when(userService.getUserById(99L)).thenThrow(new Exception("User not found"));

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("User not found"));
    }

    @Test
    void getUserById_returns500OnOtherFailure() throws Exception {
        when(userService.getUserById(1L)).thenThrow(new Exception("boom"));

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("Error retrieving user"));
    }

    @Test
    void getUserByUsername_returnsResponse() throws Exception {
        when(userService.getUserByUsername("alice"))
                .thenReturn(createUser(UUID.randomUUID(), "alice", "alice@example.com", Role.USER));

        mockMvc.perform(get("/users/get-by-username").param("username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void getUserByUsername_returns404WhenNotFound() throws Exception {
        when(userService.getUserByUsername("ghost")).thenThrow(new Exception("User not found"));

        mockMvc.perform(get("/users/get-by-username").param("username", "ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("User not found"));
    }

    @Test
    void getUserByUsernameForAuth_returnsResponse() throws Exception {
        when(userService.getUserByUsername("alice"))
                .thenReturn(createUser(UUID.randomUUID(), "alice", "alice@example.com", Role.USER));

        mockMvc.perform(get("/users/auth/get-by-username").param("username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getUserByUsernameForAuth_returns404WhenNotFound() throws Exception {
        when(userService.getUserByUsername("ghost")).thenThrow(new Exception("User not found"));

        mockMvc.perform(get("/users/auth/get-by-username").param("username", "ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("User not found"));
    }

    @Test
    void deleteUser_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("User deleted successfully"));

        verify(userService).deleteUser(1L);
    }

    @Test
    void deleteUser_returns404WhenNotFound() throws Exception {
        doThrow(new Exception("User not found")).when(userService).deleteUser(99L);

        mockMvc.perform(delete("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("User not found"));
    }

    @Test
    void deleteUser_returns500OnOtherFailure() throws Exception {
        doThrow(new Exception("db down")).when(userService).deleteUser(1L);

        mockMvc.perform(delete("/users/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("Error deleting user"));
    }
}
