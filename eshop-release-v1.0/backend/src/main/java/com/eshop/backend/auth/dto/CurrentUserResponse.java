package com.eshop.backend.auth.dto;

public record CurrentUserResponse(Long userId, String username, String nickname, String phone, String role) {
}
