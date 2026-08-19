package com.eshop.backend.vehicle;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.vehicle.dto.VehicleConfiguratorResponse;
import com.eshop.backend.vehicle.dto.VehicleQuoteRequest;
import com.eshop.backend.vehicle.dto.VehicleQuoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vehicles")
@RequiredArgsConstructor
public class VehicleConfiguratorController {
    private final VehicleConfigurationService configurationService;

    @GetMapping("/{productId}/configurator")
    public ApiResponse<VehicleConfiguratorResponse> configurator(@PathVariable Long productId) {
        return ApiResponse.success(configurationService.getConfigurator(productId));
    }

    @PostMapping("/{productId}/configurator/quote")
    public ApiResponse<VehicleQuoteResponse> quote(
            @PathVariable Long productId,
            @Valid @RequestBody VehicleQuoteRequest request) {
        return ApiResponse.success(configurationService.quote(productId, request.skuId(), request.optionValueIds()));
    }
}
