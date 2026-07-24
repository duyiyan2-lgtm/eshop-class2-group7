package com.eshop.backend.auth.dto;

public record AuthResponse(Long userId, String username, String nickname, String role, String token) {
}
