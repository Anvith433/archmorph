package com.anvith.archmorph.upload.controller;
import com.anvith.archmorph.common.response.ApiResponse;
import com.anvith.archmorph.upload.dto.UploadResponse;
import com.anvith.archmorph.upload.service.ProjectUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class UploadController {

    private final ProjectUploadService uploadService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<UploadResponse>> uploadProject(
            @RequestParam("file") MultipartFile file) {

        UploadResponse response = uploadService.uploadProject(file);

        return ResponseEntity.ok(
                ApiResponse.<UploadResponse>builder()
                        .success(true)
                        .message("Project uploaded successfully.")
                        .data(response)
                        .build()
        );
    }
}