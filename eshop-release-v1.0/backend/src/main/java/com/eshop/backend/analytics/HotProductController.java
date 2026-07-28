package com.eshop.backend.analytics;

import com.eshop.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class HotProductController {
    private final HotProductService hotProductService;

    @GetMapping("/products/hot-ranking")
    public ApiResponse<List<HotProductResponse>> list(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.success(hotProductService.list(days, limit));
    }
}
