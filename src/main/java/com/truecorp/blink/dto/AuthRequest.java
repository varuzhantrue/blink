package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AuthRequest {
    @Schema(description = "Account username", example = "admin")
    private String username;

    @Schema(description = "Account password", example = "password123")
    private String password;
}
