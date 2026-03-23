package com.truecorp.blink.dto;

import java.time.Instant;

public record FileMetadataResponse(
        Long id,
        String originalFileName,
        String s3ObjectKey,
        String contentType,
        long fileSize,
        Instant uploadTimestamp,
        String ownerUsername
) {
}
