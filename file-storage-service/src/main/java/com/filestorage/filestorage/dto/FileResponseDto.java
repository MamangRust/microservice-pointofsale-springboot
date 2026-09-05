package com.filestorage.filestorage.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponseDto {
    private UUID id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String downloadUrl;
    private UUID uploadedBy;
    private LocalDateTime uploadedAt;
}
