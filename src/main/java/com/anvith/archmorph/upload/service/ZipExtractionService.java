package com.anvith.archmorph.upload.service;

import java.nio.file.Path;

public interface ZipExtractionService {

    /**
     * Safely extract {@code archive} into the empty directory {@code destination}.
     * Rejects path traversal, absolute paths, symlinks, duplicate entries and
     * archives that exceed the configured size, count or ratio limits.
     */
    ExtractionReport extract(Path archive, Path destination);
}
