package com.anvith.archmorph.upload.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    private String projectId;

    private String projectName;

    private long fileSize;

    private String status;

}