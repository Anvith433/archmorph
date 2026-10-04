package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.InvalidZipException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * First-line validation of an uploaded archive: presence, size, extension
 * and ZIP magic bytes. Content-level checks happen during extraction.
 */
@Service
public class ProjectValidator {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final ArchMorphProperties properties;

    public ProjectValidator(ArchMorphProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file) {
        if (file == null) {
            throw new InvalidZipException("No file uploaded.");
        }
        if (file.isEmpty()) {
            throw new InvalidZipException("Uploaded file is empty.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.trim().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new InvalidZipException("Only ZIP files are allowed.");
        }
        long max = properties.getUpload().getMaxArchiveSize().toBytes();
        if (file.getSize() > max) {
            throw new InvalidZipException(ErrorCode.ARCHIVE_TOO_LARGE,
                    "The archive exceeds the maximum size of " + (max / (1024 * 1024)) + " MB.",
                    "Remove generated directories such as target/ and node_modules/ and upload again.");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(4);
            for (int i = 0; i < ZIP_MAGIC.length; i++) {
                if (header.length < 4 || header[i] != ZIP_MAGIC[i]) {
                    throw new InvalidZipException("The uploaded file is not a valid ZIP archive.");
                }
            }
        } catch (IOException e) {
            throw new InvalidZipException("The uploaded file could not be read.");
        }
    }
}
