package com.anvith.archmorph.upload.service;

import java.util.List;

/**
 * Summary of a secure extraction.
 *
 * @param extractedFiles      regular files written
 * @param skippedEntries      entries skipped because they are generated/IDE content
 * @param uncompressedBytes   total bytes written
 * @param skippedDirectories  distinct ignored directory names encountered
 */
public record ExtractionReport(int extractedFiles, int skippedEntries, long uncompressedBytes,
                               List<String> skippedDirectories) {
}
