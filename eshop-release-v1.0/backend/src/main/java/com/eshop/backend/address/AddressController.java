package com.eshop.backend.address;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class AddressController {
    private final AddressService addressService;

    @GetMapping
    public ApiResponse<List<UserAddress>> list(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success(addressService.list(user.getUserId()));
    }

    @PostMapping
    public ApiResponse<UserAddress> create(
            @AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.success(addressService.create(user.getUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserAddress> update(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.success(addressService.update(user.getUserId(), id, request));
    }

    @PatchMapping("/{id}/default")
    public ApiResponse<UserAddress> setDefault(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long id) {
        return ApiResponse.success(addressService.setDefault(user.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long id) {
        addressService.delete(user.getUserId(), id);
        return ApiResponse.success();
    }
}
