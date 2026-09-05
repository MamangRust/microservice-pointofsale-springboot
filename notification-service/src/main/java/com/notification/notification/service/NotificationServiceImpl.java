package com.notification.notification.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.common.dto.NotificationDto;
import com.common.dto.FileMetadataDto;
import com.notification.notification.client.FileStorageClient;
import com.notification.notification.entity.Notification;
import com.notification.notification.repository.NotificationRepository;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.http.ResponseEntity;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final Tracer tracer;
    private final FileStorageClient fileStorageClient;

    public NotificationServiceImpl(NotificationRepository notificationRepository, Tracer tracer, FileStorageClient fileStorageClient) {
        this.notificationRepository = notificationRepository;
        this.tracer = tracer;
        this.fileStorageClient = fileStorageClient;
    }

    @Override
    public Notification sendNotification(NotificationDto dto) {
        Span span = tracer.spanBuilder("send-notification")
                .setAttribute("notification.recipient", dto.getRecipient())
                .setAttribute("notification.type", dto.getType())
                .startSpan();

        try {
            logger.info("Sending {} notification to {}: {}", dto.getType(), dto.getRecipient(), dto.getTitle());

            // Bi-directional resolution: If notification contains a file-id, enrich message with file details!
            String enrichedMessage = dto.getMessage();
            if (enrichedMessage.contains("file-id:")) {
                try {
                    String[] parts = enrichedMessage.split("file-id:");
                    if (parts.length > 1) {
                        String rawUuid = parts[1].trim().split("\\s+")[0];
                        // Clean up trailing punctuation if any
                        rawUuid = rawUuid.replaceAll("[.,;:!?]$", "");
                        UUID fileId = UUID.fromString(rawUuid);
                        ResponseEntity<FileMetadataDto> fileResponse = fileStorageClient.getFileMetadata(fileId);
                        if (fileResponse.getStatusCode().is2xxSuccessful() && fileResponse.getBody() != null) {
                            FileMetadataDto fileMeta = fileResponse.getBody();
                            enrichedMessage = enrichedMessage + " [Resolved File: name='" + fileMeta.getFileName() + "', size=" + fileMeta.getFileSize() + " bytes]";
                            logger.info("Successfully enriched notification with file metadata for ID: {}", fileId);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to enrich notification with file details: {}", e.getMessage());
                }
            }

            // Simulate actual sending (e.g. SMTP/SMS Gateway)
            String status = "SENT"; 

            Notification notification = Notification.builder()
                    .userId(dto.getUserId())
                    .recipient(dto.getRecipient())
                    .title(dto.getTitle())
                    .message(enrichedMessage)
                    .type(dto.getType())
                    .status(status)
                    .build();

            Notification savedNotification = notificationRepository.save(notification);
            
            span.setAttribute("notification.id", savedNotification.getId().toString());
            span.setAttribute("notification.status", status);
            return savedNotification;
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage());
            span.recordException(e);
            
            Notification notification = Notification.builder()
                    .userId(dto.getUserId())
                    .recipient(dto.getRecipient())
                    .title(dto.getTitle())
                    .message(dto.getMessage()) // use original message in case of failure prior to resolution or generic failure
                    .type(dto.getType())
                    .status("FAILED")
                    .build();
            
            return notificationRepository.save(notification);
        } finally {
            span.end();
        }
    }

    @Override
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    @Override
    public List<Notification> getNotificationsByUserId(UUID userId) {
        return notificationRepository.findByUserId(userId);
    }

    @Override
    public Notification getNotificationById(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found with ID: " + id));
    }
}
