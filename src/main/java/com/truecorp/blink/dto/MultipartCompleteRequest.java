package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MultipartCompleteRequest(
        @NotEmpty
        @Schema(description = "Completed parts, in ascending part-number order") List<Part> parts
) {
    public record Part(
            @NotNull @Min(1)
            @Schema(description = "Part number (1-based)") Integer partNumber,

            @NotBlank
            @Schema(description = "ETag returned by MinIO for this part") String eTag
    ) {
    }
}
