package com.eshop.backend.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HotProductService {
    private final HotProductMapper hotProductMapper;

    @Transactional(readOnly = true)
    public List<HotProductResponse> list(int days, int limit) {
        int normalizedDays = Math.min(Math.max(days, 0), 365);
        int normalizedLimit = Math.min(Math.max(limit, 1), 20);
        LocalDateTime cutoff = normalizedDays == 0
                ? null
                : LocalDateTime.now().minusDays(normalizedDays);
        return hotProductMapper.selectHotProducts(cutoff, normalizedLimit);
    }
}
