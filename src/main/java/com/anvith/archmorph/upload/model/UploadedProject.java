package com.anvith.archmorph.upload.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.file.Path;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadedProject {

    private String projectId;

    private String projectName;

    private Path archivePath;

    private Path projectRoot;

    private long fileSize;
}