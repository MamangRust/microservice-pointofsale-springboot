package com.filestorage.filestorage.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.filestorage.filestorage.dto.FileResponseDto;
import com.filestorage.filestorage.entity.FileMetadata;
import com.filestorage.filestorage.service.FileStorageService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/files")
public class FileStorageController {
    private final FileStorageService fileStorageService;

    public FileStorageController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileResponseDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) UUID uploadedBy) {
        FileMetadata metadata = fileStorageService.storeFile(file, uploadedBy);
        FileResponseDto response = mapToResponseDto(metadata);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id, HttpServletRequest request) {
        Resource resource = fileStorageService.loadFileAsResource(id);
        FileMetadata metadata = fileStorageService.getFileMetadata(id);

        // Try to determine file's content type dynamically
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            // Keep it null
        }

        // Fallback to the default system binary type if type could not be determined
        if (contentType == null) {
            contentType = metadata.getFileType() != null ? metadata.getFileType() : "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFileName() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileResponseDto> getFileMetadata(@PathVariable UUID id) {
        FileMetadata metadata = fileStorageService.getFileMetadata(id);
        return ResponseEntity.ok(mapToResponseDto(metadata));
    }

    @GetMapping
    public ResponseEntity<List<FileResponseDto>> getAllFiles() {
        List<FileResponseDto> files = fileStorageService.getAllFiles().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FileResponseDto>> getFilesByUserId(@PathVariable UUID userId) {
        List<FileResponseDto> files = fileStorageService.getFilesByUserId(userId).stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID id) {
        fileStorageService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    private FileResponseDto mapToResponseDto(FileMetadata metadata) {
        String downloadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/files/download/")
                .path(metadata.getId().toString())
                .toUriString();

        return FileResponseDto.builder()
                .id(metadata.getId())
                .fileName(metadata.getFileName())
                .fileType(metadata.getFileType())
                .fileSize(metadata.getFileSize())
                .downloadUrl(downloadUrl)
                .uploadedBy(metadata.getUploadedBy())
                .uploadedAt(metadata.getUploadedAt())
                .build();
    }
}
