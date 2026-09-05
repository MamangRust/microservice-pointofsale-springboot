package com.user.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.user.user.dto.UserMapper;
import com.user.user.dto.UserMapperImpl;
import com.user.user.dto.UserRequest;
import com.user.user.entity.User;
import com.user.user.enums.Role;
import com.user.user.repository.UserRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;

    private UserService userService;

    private final UserMapper userMapper = new UserMapperImpl();

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, userMapper, passwordEncoder, OpenTelemetry.noop());
    }

    private User createUser(UUID id, String username, String password, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
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
    void createUser_encodesPasswordAndForcesDefaultRole() throws Exception {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User result = userService.createUser(createRequest("newuser", "raw-secret", "newuser@example.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("newuser");
        assertThat(saved.getEmail()).isEqualTo("newuser@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("raw-secret");
        assertThat(passwordEncoder.matches("raw-secret", saved.getPassword())).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getId()).isNotNull();
        assertThat(result.getId()).isEqualTo(saved.getId());
    }

    @Test
    void createUser_throwsWhenUsernameAlreadyExists() {
        when(userRepository.findByUsername("taken"))
                .thenReturn(Optional.of(createUser(UUID.randomUUID(), "taken", "pw", "taken@example.com", Role.USER)));

        assertThatThrownBy(() -> userService.createUser(createRequest("taken", "pw", "taken@example.com")))
                .isInstanceOf(Exception.class)
                .hasMessage("User already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserByUsername_returnsUserWhenFound() throws Exception {
        User user = createUser(UUID.randomUUID(), "alice", "pw", "alice@example.com", Role.USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        User result = userService.getUserByUsername("alice");

        assertThat(result).isSameAs(user);
        verify(userRepository).findByUsername("alice");
    }

    @Test
    void getUserByUsername_throwsWhenNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByUsername("ghost"))
                .isInstanceOf(Exception.class)
                .hasMessage("User not found");
    }

    @Test
    void getUserById_returnsUserWhenFound() throws Exception {
        User user = createUser(UUID.randomUUID(), "bob", "pw", "bob@example.com", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getUserById(1L);

        assertThat(result).isSameAs(user);
        verify(userRepository).findById(1L);
    }

    @Test
    void getUserById_throwsWhenNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(Exception.class)
                .hasMessage("User not found");
    }

    @Test
    void getAllUsers_returnsAllFromRepository() {
        User u1 = createUser(UUID.randomUUID(), "alice", "pw", "alice@example.com", Role.USER);
        User u2 = createUser(UUID.randomUUID(), "bob", "pw", "bob@example.com", Role.ADMIN);
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        List<User> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(User::getUsername).containsExactly("alice", "bob");
        verify(userRepository).findAll();
    }

    @Test
    void deleteUser_deletesUserWhenExists() throws Exception {
        User user = createUser(UUID.randomUUID(), "alice", "pw", "alice@example.com", Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_throwsWhenNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(Exception.class)
                .hasMessage("User not found");

        verify(userRepository, never()).delete(any(User.class));
    }
}
