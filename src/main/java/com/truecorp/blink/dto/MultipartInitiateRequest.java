package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MultipartInitiateRequest(
        @NotBlank
        @Schema(description = "Original filename") String fileName,

        @NotBlank
        @Schema(description = "MIME type of the file, e.g. video/mp4") String contentType,

        @NotNull
        @Min(1)
        @Schema(description = "Total file size in bytes") Long fileSize,

        @NotNull
        @Min(1)
        @Schema(description = "Number of parts the client will upload") Integer partCount
) {
}
