package com.common.dto;

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
public class FileMetadataDto {
    private UUID id;
    private String fileName;
    private String fileType;
    private long fileSize;
    private UUID uploadedBy;
    private LocalDateTime uploadedAt;
}
