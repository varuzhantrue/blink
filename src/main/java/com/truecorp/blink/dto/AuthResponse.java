package com.truecorp.blink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    @Schema(description = "JWT Bearer token - pass as 'Authorization: Bearer <token>'. Expires after 24 hours.")
    private String token;
}
