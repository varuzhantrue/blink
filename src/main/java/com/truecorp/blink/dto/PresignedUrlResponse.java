package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PresignedUrlResponse(
        @Schema(description = "Time-limited presigned URL for direct file download. Valid for 1 hour.") String url
) {
}
