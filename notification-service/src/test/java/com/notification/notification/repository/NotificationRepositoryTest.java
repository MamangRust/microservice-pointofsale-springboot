package com.notification.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.notification.notification.entity.Notification;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class NotificationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private NotificationRepository notificationRepository;

    private Notification createNotification(UUID userId, String recipient, String title) {
        return Notification.builder()
                .userId(userId)
                .recipient(recipient)
                .title(title)
                .message("Hello " + title)
                .type("EMAIL")
                .status("SENT")
                .build();
    }

    @Test
    void save_generatesUuidIdAndKeepsFields() {
        UUID userId = UUID.randomUUID();
        Notification saved = notificationRepository.save(createNotification(userId, "user@example.com", "Welcome"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getRecipient()).isEqualTo("user@example.com");
        assertThat(saved.getTitle()).isEqualTo("Welcome");
        assertThat(saved.getMessage()).isEqualTo("Hello Welcome");
        assertThat(saved.getType()).isEqualTo("EMAIL");
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findById_returnsSavedNotification() {
        Notification saved = notificationRepository.save(
                createNotification(UUID.randomUUID(), "user@example.com", "Persisted"));

        Optional<Notification> found = notificationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Persisted");
        assertThat(found.get().getUserId()).isEqualTo(saved.getUserId());
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(notificationRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByUserId_returnsOnlyNotificationsOfThatUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        notificationRepository.save(createNotification(userA, "a@example.com", "A1"));
        notificationRepository.save(createNotification(userB, "b@example.com", "B1"));
        notificationRepository.save(createNotification(userA, "a@example.com", "A2"));

        List<Notification> result = notificationRepository.findByUserId(userA);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Notification::getTitle).containsExactlyInAnyOrder("A1", "A2");
        assertThat(result).allSatisfy(n -> assertThat(n.getUserId()).isEqualTo(userA));
    }

    @Test
    void findByUserId_returnsEmptyWhenNoMatch() {
        notificationRepository.save(createNotification(UUID.randomUUID(), "a@example.com", "A1"));

        assertThat(notificationRepository.findByUserId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_returnsAllPersisted() {
        notificationRepository.save(createNotification(UUID.randomUUID(), "a@example.com", "A1"));
        notificationRepository.save(createNotification(UUID.randomUUID(), "b@example.com", "B1"));

        List<Notification> all = notificationRepository.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(Notification::getTitle).containsExactlyInAnyOrder("A1", "B1");
    }
}
