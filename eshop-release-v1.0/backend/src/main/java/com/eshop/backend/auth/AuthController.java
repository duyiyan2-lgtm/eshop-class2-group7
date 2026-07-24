package com.eshop.backend.auth;

import com.eshop.backend.auth.dto.AuthResponse;
import com.eshop.backend.auth.dto.CurrentUserResponse;
import com.eshop.backend.auth.dto.LoginRequest;
import com.eshop.backend.auth.dto.RegisterRequest;
import com.eshop.backend.auth.dto.UserProfileRequest;
import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success();
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // JWT is stateless. The client clears its token; a future Redis deny-list can be added if required.
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success(authService.currentUser(user));
    }

    @PutMapping("/me")
    public ApiResponse<CurrentUserResponse> updateProfile(
            @AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody UserProfileRequest request) {
        return ApiResponse.success(authService.updateProfile(user, request));
    }
}
