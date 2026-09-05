package com.product.product.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.common.dto.FileMetadataDto;

@FeignClient(name = "file-storage-service", path = "/files")
public interface FileStorageClient {

    @GetMapping("/{id}")
    ResponseEntity<FileMetadataDto> getFileMetadata(@PathVariable("id") UUID id);
}
