package com.eshop.backend.review;

import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ReviewImages {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_IMAGES = 3;
    private static final String UPLOAD_PREFIX = "/api/uploads/";

    private ReviewImages() {
    }

    static List<String> normalize(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        if (imageUrls.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.REVIEW_IMAGE_LIMIT);
        }
        List<String> normalized = new ArrayList<>(imageUrls.size());
        for (String raw : imageUrls) {
            if (!StringUtils.hasText(raw)) {
                throw new BusinessException(ErrorCode.REVIEW_IMAGE_INVALID);
            }
            String url = raw.trim();
            if (!url.startsWith(UPLOAD_PREFIX) || url.contains("..") || url.length() > 500) {
                throw new BusinessException(ErrorCode.REVIEW_IMAGE_INVALID);
            }
            normalized.add(url);
        }
        return List.copyOf(normalized);
    }

    static String toJson(List<String> imageUrls) {
        List<String> normalized = normalize(imageUrls);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.REVIEW_IMAGE_INVALID);
        }
    }

    static List<String> fromJson(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return List.of();
        }
        try {
            List<String> parsed = MAPPER.readValue(imagesJson, new TypeReference<>() {
            });
            return parsed == null ? List.of() : Collections.unmodifiableList(parsed);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
