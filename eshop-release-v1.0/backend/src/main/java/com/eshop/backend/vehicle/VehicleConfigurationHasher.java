package com.eshop.backend.vehicle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class VehicleConfigurationHasher {
    public static final String EMPTY_HASH = "NONE";

    private VehicleConfigurationHasher() {
    }

    public static List<Long> normalizeIds(Collection<Long> optionValueIds) {
        if (optionValueIds == null || optionValueIds.isEmpty()) {
            return List.of();
        }
        return optionValueIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public static String hash(Collection<Long> optionValueIds) {
        List<Long> normalized = normalizeIds(optionValueIds);
        if (normalized.isEmpty()) {
            return EMPTY_HASH;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalized.toString().getBytes(StandardCharsets.UTF_8));
            return "v1:" + HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to hash vehicle configurations", exception);
        }
    }
}
