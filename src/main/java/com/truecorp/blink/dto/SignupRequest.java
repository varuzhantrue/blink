package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {
    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 255, message = "Username must be between 3 and 255 characters")
    @Schema(description = "Desired username", example = "alice", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "Account password (min 8 characters)", example = "secret123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
