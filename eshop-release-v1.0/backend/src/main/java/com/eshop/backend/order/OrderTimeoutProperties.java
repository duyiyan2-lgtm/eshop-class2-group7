package com.eshop.backend.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order-timeout")
public record OrderTimeoutProperties(
        boolean enabled,
        long minutes,
        int batchSize
) {
    public long safeMinutes() {
        return Math.max(minutes, 1);
    }

    public int safeBatchSize() {
        return Math.min(Math.max(batchSize, 1), 500);
    }
}
