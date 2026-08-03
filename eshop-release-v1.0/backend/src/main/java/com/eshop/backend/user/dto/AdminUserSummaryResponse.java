package com.eshop.backend.user.dto;

public record AdminUserSummaryResponse(
        long managedAccountCount,
        long buyerCount,
        long sellerCount,
        long enabledCount,
        long disabledCount
) {
}
