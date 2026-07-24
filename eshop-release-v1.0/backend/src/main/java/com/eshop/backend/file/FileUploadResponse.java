package com.eshop.backend.file;

public record FileUploadResponse(
        String url,
        String filename,
        long size
) {
}
