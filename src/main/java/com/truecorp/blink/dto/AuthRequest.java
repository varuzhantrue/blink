package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Username must not be blank")
    @Size(max = 255, message = "Username must not exceed 255 characters")
    @Schema(description = "Account username", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Schema(description = "Account password", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
