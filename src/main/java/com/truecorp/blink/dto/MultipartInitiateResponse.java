package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MultipartInitiateResponse(
        @Schema(description = "Internal file ID — pass to /complete and /abort") Long fileId,
        @Schema(description = "MinIO multipart upload ID") String uploadId,
        @Schema(description = "Presigned PUT URLs, one per part, in order") List<String> partUrls
) {
}
