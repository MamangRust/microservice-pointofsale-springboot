package com.user.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.user.user.entity.User;
import com.user.user.enums.Role;

// NOTE: UserRepository declares JpaRepository<User, Long> while the User entity
// primary key is a UUID. The Long-typed findById/deleteById methods are therefore
// NOT exercised here (runtime type mismatch); tests rely on save / findByUsername /
// findAll / delete(entity), which are ID-type agnostic.
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private UserRepository userRepository;

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("encoded-pw");
        user.setEmail(email);
        user.setRole(Role.USER);
        return user;
    }

    @Test
    void save_persistsUserWithGeneratedUuidId() {
        User saved = userRepository.save(createUser("alice", "alice@example.com"));
        userRepository.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);
    }

    @Test
    void findByUsername_returnsSavedUser() {
        User saved = userRepository.save(createUser("bob", "bob@example.com"));

        Optional<User> found = userRepository.findByUsername("bob");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUsername()).isEqualTo("bob");
        assertThat(found.get().getEmail()).isEqualTo("bob@example.com");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void findByUsername_returnsEmptyWhenMissing() {
        Optional<User> found = userRepository.findByUsername("ghost");

        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsAllPersisted() {
        userRepository.save(createUser("alice", "alice@example.com"));
        userRepository.save(createUser("bob", "bob@example.com"));

        List<User> all = userRepository.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(User::getUsername).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void save_withDuplicateUsername_isRejectedByUniqueConstraint() {
        userRepository.saveAndFlush(createUser("alice", "alice@example.com"));

        User duplicate = createUser("alice", "other@example.com");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(Exception.class);
    }

    @Test
    void delete_removesRow() {
        User saved = userRepository.save(createUser("deleteme", "deleteme@example.com"));

        userRepository.delete(saved);
        userRepository.flush();

        assertThat(userRepository.findByUsername("deleteme")).isEmpty();
        assertThat(userRepository.findAll()).isEmpty();
    }
}
