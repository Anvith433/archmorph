package com.anvith.archmorph.upload.service;

import java.nio.file.Path;

public interface ZipExtractionService {

    void extractArchive(String projectId, Path archivePath);

}