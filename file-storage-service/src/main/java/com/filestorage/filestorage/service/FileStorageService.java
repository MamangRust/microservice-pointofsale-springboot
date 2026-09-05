package com.filestorage.filestorage.service;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.filestorage.filestorage.entity.FileMetadata;

public interface FileStorageService {
    FileMetadata storeFile(MultipartFile file, UUID uploadedBy);
    Resource loadFileAsResource(UUID id);
    void deleteFile(UUID id);
    FileMetadata getFileMetadata(UUID id);
    List<FileMetadata> getAllFiles();
    List<FileMetadata> getFilesByUserId(UUID uploadedBy);
}
