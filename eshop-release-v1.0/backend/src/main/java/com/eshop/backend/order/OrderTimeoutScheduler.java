package com.eshop.backend.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.order-timeout", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OrderTimeoutScheduler {
    private static final int MAX_BATCHES_PER_SCAN = 10;

    private final OrderService orderService;
    private final OrderTimeoutProperties properties;

    @Scheduled(
            initialDelayString = "${app.order-timeout.initial-delay-ms:15000}",
            fixedDelayString = "${app.order-timeout.scan-delay-ms:60000}")
    public void cancelExpiredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(properties.safeMinutes());
        int canceledCount = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_SCAN; batch++) {
            List<Long> orderIds = orderService.findExpiredPendingOrderIds(cutoff, properties.safeBatchSize());
            if (orderIds.isEmpty()) {
                break;
            }
            int canceledInBatch = 0;
            for (Long orderId : orderIds) {
                try {
                    if (orderService.cancelExpiredOrder(orderId, cutoff)) {
                        canceledCount++;
                        canceledInBatch++;
                    }
                } catch (RuntimeException exception) {
                    log.error("Failed to cancel expired order {}", orderId, exception);
                }
            }
            if (orderIds.size() < properties.safeBatchSize() || canceledInBatch == 0) {
                break;
            }
        }
        if (canceledCount > 0) {
            log.info("Canceled {} expired pending order(s), cutoff={}", canceledCount, cutoff);
        }
    }
}
