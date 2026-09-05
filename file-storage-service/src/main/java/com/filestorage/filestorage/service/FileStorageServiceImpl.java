package com.filestorage.filestorage.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.common.dto.NotificationDto;
import com.filestorage.filestorage.client.NotificationClient;
import com.filestorage.filestorage.entity.FileMetadata;
import com.filestorage.filestorage.repository.FileMetadataRepository;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.PostConstruct;

@Service
public class FileStorageServiceImpl implements FileStorageService {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final Tracer tracer;
    private final NotificationClient notificationClient;
    private final Path fileStorageLocation;

    public FileStorageServiceImpl(
            FileMetadataRepository fileMetadataRepository,
            Tracer tracer,
            NotificationClient notificationClient,
            @Value("${file.upload-dir:uploads/}") String uploadDir) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.tracer = tracer;
        this.notificationClient = notificationClient;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.fileStorageLocation);
            logger.info("Created upload directory at: {}", this.fileStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @Override
    public FileMetadata storeFile(MultipartFile file, UUID uploadedBy) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        
        Span span = tracer.spanBuilder("store-file")
                .setAttribute("file.original_name", originalFileName)
                .setAttribute("file.size", file.getSize())
                .setAttribute("file.content_type", file.getContentType())
                .startSpan();

        try {
            // Check if the filename contains invalid characters
            if (originalFileName.contains("..")) {
                throw new RuntimeException("Filename contains invalid path sequence " + originalFileName);
            }

            // Generate a unique filename using UUID prefix
            String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;
            Path targetLocation = this.fileStorageLocation.resolve(uniqueFileName);
            
            // Copy file to target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            logger.info("File successfully written to filesystem: {}", targetLocation);

            FileMetadata fileMetadata = FileMetadata.builder()
                    .fileName(originalFileName)
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .filePath(targetLocation.toString())
                    .uploadedBy(uploadedBy)
                    .build();

            FileMetadata savedMetadata = fileMetadataRepository.save(fileMetadata);
            span.setAttribute("file.metadata_id", savedMetadata.getId().toString());

            // Send notification to notification-service via Feign Client
            try {
                UUID recipientUserId = uploadedBy != null ? uploadedBy : UUID.nameUUIDFromBytes("SYSTEM".getBytes());
                NotificationDto notification = NotificationDto.builder()
                        .userId(recipientUserId)
                        .recipient("user-system@microservices.local")
                        .title("File Upload Successful")
                        .message("File " + originalFileName + " was successfully uploaded. Size: " + file.getSize() + " bytes.")
                        .type("EMAIL")
                        .build();
                notificationClient.sendNotification(notification);
                logger.info("Sent upload notification for file: {}", originalFileName);
            } catch (Exception e) {
                logger.error("Failed to send file upload notification: {}", e.getMessage());
            }

            return savedMetadata;
        } catch (IOException ex) {
            span.recordException(ex);
            throw new RuntimeException("Could not store file " + originalFileName + ". Please try again!", ex);
        } finally {
            span.end();
        }
    }

    @Override
    public Resource loadFileAsResource(UUID id) {
        Span span = tracer.spanBuilder("load-file-resource")
                .setAttribute("file.id", id.toString())
                .startSpan();

        try {
            FileMetadata fileMetadata = getFileMetadata(id);
            Path filePath = Paths.get(fileMetadata.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                span.setAttribute("file.name", fileMetadata.getFileName());
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + fileMetadata.getFileName());
            }
        } catch (MalformedURLException ex) {
            span.recordException(ex);
            throw new RuntimeException("File path is malformed.", ex);
        } finally {
            span.end();
        }
    }

    @Override
    public void deleteFile(UUID id) {
        Span span = tracer.spanBuilder("delete-file")
                .setAttribute("file.id", id.toString())
                .startSpan();

        try {
            FileMetadata fileMetadata = getFileMetadata(id);
            Path filePath = Paths.get(fileMetadata.getFilePath()).normalize();
            
            // Delete file from storage
            try {
                Files.deleteIfExists(filePath);
                logger.info("File deleted from filesystem: {}", filePath);
            } catch (IOException e) {
                logger.warn("Could not delete file from filesystem: {}", filePath, e);
            }
            
            // Delete metadata from database
            fileMetadataRepository.delete(fileMetadata);
            logger.info("File metadata deleted from database for ID: {}", id);

            // Send notification to notification-service via Feign Client
            try {
                UUID recipientUserId = fileMetadata.getUploadedBy() != null ? fileMetadata.getUploadedBy() : UUID.nameUUIDFromBytes("SYSTEM".getBytes());
                NotificationDto notification = NotificationDto.builder()
                        .userId(recipientUserId)
                        .recipient("user-system@microservices.local")
                        .title("File Deletion Successful")
                        .message("File " + fileMetadata.getFileName() + " was deleted successfully.")
                        .type("EMAIL")
                        .build();
                notificationClient.sendNotification(notification);
                logger.info("Sent deletion notification for file ID: {}", id);
            } catch (Exception e) {
                logger.error("Failed to send file deletion notification: {}", e.getMessage());
            }
        } finally {
            span.end();
        }
    }

    @Override
    public FileMetadata getFileMetadata(UUID id) {
        return fileMetadataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found with ID: " + id));
    }

    @Override
    public List<FileMetadata> getAllFiles() {
        return fileMetadataRepository.findAll();
    }

    @Override
    public List<FileMetadata> getFilesByUserId(UUID uploadedBy) {
        return fileMetadataRepository.findByUploadedBy(uploadedBy);
    }
}
