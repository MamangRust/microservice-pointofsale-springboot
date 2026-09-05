package com.notification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.stubbing.Answer;
import org.springframework.http.ResponseEntity;

import com.common.dto.FileMetadataDto;
import com.common.dto.NotificationDto;
import com.notification.notification.client.FileStorageClient;
import com.notification.notification.entity.Notification;
import com.notification.notification.repository.NotificationRepository;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FileStorageClient fileStorageClient;

    private NotificationServiceImpl notificationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        Tracer tracer = OpenTelemetry.noop().getTracer("test");
        notificationService = new NotificationServiceImpl(notificationRepository, tracer, fileStorageClient);
        userId = UUID.randomUUID();
    }

    private NotificationDto dto(String message) {
        return NotificationDto.builder()
                .userId(userId)
                .recipient("user@example.com")
                .title("Test Title")
                .message(message)
                .type("EMAIL")
                .build();
    }

    /** Save answer that copies the built entity and generates an id (as the DB would). */
    private Answer<Notification> withGeneratedId() {
        return inv -> {
            Notification src = inv.getArgument(0);
            return Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(src.getUserId())
                    .recipient(src.getRecipient())
                    .title(src.getTitle())
                    .message(src.getMessage())
                    .type(src.getType())
                    .status(src.getStatus())
                    .createdAt(src.getCreatedAt())
                    .build();
        };
    }

    private ResponseEntity<FileMetadataDto> fileResponse(String fileName, long size) {
        return ResponseEntity.ok(FileMetadataDto.builder()
                .id(UUID.randomUUID())
                .fileName(fileName)
                .fileType("application/pdf")
                .fileSize(size)
                .build());
    }

    @Test
    void sendNotification_savesAndReturnsNotificationWithSentStatus() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(withGeneratedId());

        Notification result = notificationService.sendNotification(dto("Your order has shipped"));

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SENT");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getRecipient()).isEqualTo("user@example.com");
        assertThat(saved.getTitle()).isEqualTo("Test Title");
        assertThat(saved.getMessage()).isEqualTo("Your order has shipped");
        assertThat(saved.getType()).isEqualTo("EMAIL");
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void sendNotification_enrichesMessageWhenFileIdPresent() {
        UUID fileId = UUID.fromString("3f2b6c1e-1a2b-3c4d-5e6f-7a8b9c0d1e2f");
        String message = "Your report is ready, file-id: " + fileId;
        when(fileStorageClient.getFileMetadata(fileId)).thenReturn(fileResponse("report.pdf", 1024));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(withGeneratedId());

        Notification result = notificationService.sendNotification(dto(message));

        verify(fileStorageClient).getFileMetadata(fileId);
        assertThat(result.getMessage()).isEqualTo(
                message + " [Resolved File: name='report.pdf', size=1024 bytes]");
        assertThat(result.getStatus()).isEqualTo("SENT");
    }

    @Test
    void sendNotification_stripsTrailingPunctuationFromFileId() {
        UUID fileId = UUID.fromString("3f2b6c1e-1a2b-3c4d-5e6f-7a8b9c0d1e2f");
        String message = "Your report is ready, file-id: " + fileId + ".";
        when(fileStorageClient.getFileMetadata(fileId)).thenReturn(fileResponse("report.pdf", 2048));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(withGeneratedId());

        notificationService.sendNotification(dto(message));

        verify(fileStorageClient).getFileMetadata(fileId);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).endsWith("[Resolved File: name='report.pdf', size=2048 bytes]");
    }

    @Test
    void sendNotification_keepsMessageUnchangedWhenFileLookupFails() {
        UUID fileId = UUID.fromString("3f2b6c1e-1a2b-3c4d-5e6f-7a8b9c0d1e2f");
        String message = "Your report is ready, file-id: " + fileId;
        when(fileStorageClient.getFileMetadata(fileId)).thenThrow(new RuntimeException("feign connection refused"));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(withGeneratedId());

        Notification result = notificationService.sendNotification(dto(message));

        // Feign failure is swallowed — message stays as-is, notification still SENT.
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.getStatus()).isEqualTo("SENT");
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void sendNotification_keepsMessageWhenFileResponseHasNoBody() {
        UUID fileId = UUID.fromString("3f2b6c1e-1a2b-3c4d-5e6f-7a8b9c0d1e2f");
        String message = "Your report is ready, file-id: " + fileId;
        when(fileStorageClient.getFileMetadata(fileId)).thenReturn(ResponseEntity.ok().build());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(withGeneratedId());

        Notification result = notificationService.sendNotification(dto(message));

        assertThat(result.getMessage()).isEqualTo(message);
    }

    @Test
    void sendNotification_keepsMessageWhenFileResponseIsNotSuccessful() {
        UUID fileId = UUID.fromString("3f2b6c1e-1a2b-3c4d-5e6f-7a8b9c0d1e2f");
        String message = "Your report is ready, file-id: " + fileId;
        when(fileStorageClient.getFileMetadata(fileId)).thenReturn(ResponseEntity.status(404).build());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(withGeneratedId());

        Notification result = notificationService.sendNotification(dto(message));

        assertThat(result.getMessage()).isEqualTo(message);
    }

    @Test
    void sendNotification_doesNotCallFileStorageWithoutFileIdMarker() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(withGeneratedId());

        notificationService.sendNotification(dto("Plain message without marker"));

        verifyNoInteractions(fileStorageClient);
    }

    @Test
    void sendNotification_ignoresUnparseableFileIdGracefully() {
        String message = "Your report is ready, file-id: not-a-uuid";
        when(notificationRepository.save(any(Notification.class))).thenAnswer(withGeneratedId());

        Notification result = notificationService.sendNotification(dto(message));

        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.getStatus()).isEqualTo("SENT");
        verify(fileStorageClient, never()).getFileMetadata(any(UUID.class));
    }

    @Test
    void sendNotification_savesFailedCopyWhenRepositoryThrows() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("db down"))
                .thenAnswer(withGeneratedId());

        Notification result = notificationService.sendNotification(dto("Original message"));

        // Product code swallows the exception and persists a FAILED copy instead of throwing.
        verify(notificationRepository, times(2)).save(any(Notification.class));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        Notification failedCopy = captor.getAllValues().get(1);
        assertThat(failedCopy.getStatus()).isEqualTo("FAILED");
        assertThat(failedCopy.getMessage()).isEqualTo("Original message");
        assertThat(result.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void sendNotification_failedCopyUsesOriginalMessageEvenAfterEnrichment() {
        UUID fileId = UUID.fromString("3f2b6c1e-1a2b-3c4d-5e6f-7a8b9c0d1e2f");
        String message = "Your report is ready, file-id: " + fileId;
        when(fileStorageClient.getFileMetadata(fileId)).thenReturn(fileResponse("report.pdf", 512));
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("db down"))
                .thenAnswer(withGeneratedId());

        Notification result = notificationService.sendNotification(dto(message));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getMessage()).contains("[Resolved File: name='report.pdf'");
        assertThat(captor.getAllValues().get(1).getMessage()).isEqualTo(message);
        assertThat(result.getMessage()).isEqualTo(message);
    }

    @Test
    void getAllNotifications_returnsAllFromRepository() {
        Notification n1 = Notification.builder().id(UUID.randomUUID()).recipient("a@example.com")
                .title("T1").message("M1").type("EMAIL").status("SENT").build();
        Notification n2 = Notification.builder().id(UUID.randomUUID()).recipient("b@example.com")
                .title("T2").message("M2").type("SMS").status("SENT").build();
        when(notificationRepository.findAll()).thenReturn(List.of(n1, n2));

        List<Notification> result = notificationService.getAllNotifications();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Notification::getTitle).containsExactly("T1", "T2");
        verify(notificationRepository).findAll();
    }

    @Test
    void getNotificationsByUserId_returnsFromRepository() {
        Notification n1 = Notification.builder().id(UUID.randomUUID()).userId(userId)
                .recipient("a@example.com").title("T1").message("M1").type("EMAIL").status("SENT").build();
        when(notificationRepository.findByUserId(userId)).thenReturn(List.of(n1));

        List<Notification> result = notificationService.getNotificationsByUserId(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        verify(notificationRepository).findByUserId(userId);
    }

    @Test
    void getNotificationsByUserId_returnsEmptyWhenNoMatch() {
        when(notificationRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(notificationService.getNotificationsByUserId(userId)).isEmpty();
    }

    @Test
    void getNotificationById_returnsNotificationWhenFound() {
        UUID id = UUID.randomUUID();
        Notification n = Notification.builder().id(id).recipient("a@example.com")
                .title("T").message("M").type("EMAIL").status("SENT").build();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(n));

        Notification result = notificationService.getNotificationById(id);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void getNotificationById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getNotificationById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Notification not found with ID: " + id);
    }
}
