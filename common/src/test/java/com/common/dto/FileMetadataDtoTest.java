package com.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FileMetadataDtoTest {

    @Test
    void builder_setsAllFields() {
        UUID id = UUID.randomUUID();
        UUID uploader = UUID.randomUUID();
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 9, 4, 10, 0);

        FileMetadataDto dto = FileMetadataDto.builder()
                .id(id)
                .fileName("invoice.pdf")
                .fileType("application/pdf")
                .fileSize(1024L)
                .uploadedBy(uploader)
                .uploadedAt(uploadedAt)
                .build();

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getFileName()).isEqualTo("invoice.pdf");
        assertThat(dto.getFileType()).isEqualTo("application/pdf");
        assertThat(dto.getFileSize()).isEqualTo(1024L);
        assertThat(dto.getUploadedBy()).isEqualTo(uploader);
        assertThat(dto.getUploadedAt()).isEqualTo(uploadedAt);
    }

    @Test
    void allArgsConstructor_populatesAllFields() {
        UUID id = UUID.randomUUID();
        UUID uploader = UUID.randomUUID();
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 9, 4, 10, 0);

        FileMetadataDto dto = new FileMetadataDto(id, "receipt.png", "image/png",
                2048L, uploader, uploadedAt);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getFileName()).isEqualTo("receipt.png");
        assertThat(dto.getFileType()).isEqualTo("image/png");
        assertThat(dto.getFileSize()).isEqualTo(2048L);
        assertThat(dto.getUploadedBy()).isEqualTo(uploader);
        assertThat(dto.getUploadedAt()).isEqualTo(uploadedAt);
    }

    @Test
    void noArgsConstructor_yieldsDtoWithDefaults() {
        FileMetadataDto dto = new FileMetadataDto();

        assertThat(dto.getId()).isNull();
        assertThat(dto.getFileName()).isNull();
        assertThat(dto.getFileSize()).isZero();
    }

    @Test
    void equalFieldValues_yieldEqualDtos() {
        UUID id = UUID.randomUUID();
        UUID uploader = UUID.randomUUID();
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 9, 4, 10, 0);

        FileMetadataDto one = new FileMetadataDto(id, "a.pdf", "application/pdf", 1L, uploader, uploadedAt);
        FileMetadataDto two = new FileMetadataDto(id, "a.pdf", "application/pdf", 1L, uploader, uploadedAt);

        assertThat(one).isEqualTo(two);
        assertThat(one).hasSameHashCodeAs(two);
    }
}
