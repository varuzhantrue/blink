package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record FileMetadataResponse(
        @Schema(description = "Internal file ID") Long id,
        @Schema(description = "Original filename as uploaded") String originalFileName,
        @Schema(description = "UUID-based object key used in S3/MinIO storage") String s3ObjectKey,
        @Schema(description = "Media type of the file, e.g. image/png") String contentType,
        @Schema(description = "File size in bytes") long fileSize,
        @Schema(description = "UTC timestamp when the file was uploaded") Instant uploadTimestamp,
        @Schema(description = "Username of the user who uploaded the file") String ownerUsername
) {
}