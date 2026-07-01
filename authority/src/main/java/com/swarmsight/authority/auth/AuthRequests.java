package com.swarmsight.authority.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request bodies for the auth endpoints. */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank String displayName,
            @NotNull Role role,
            @NotBlank String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }
}
