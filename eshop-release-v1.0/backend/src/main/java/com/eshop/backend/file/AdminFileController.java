package com.eshop.backend.file;

import com.eshop.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/files")
@RequiredArgsConstructor
public class AdminFileController {
    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(fileUploadService.uploadImage(file));
    }
}
