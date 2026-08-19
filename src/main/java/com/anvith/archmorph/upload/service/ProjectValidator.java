package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.common.exception.InvalidZipException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectValidator {

    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    public void validate(MultipartFile file) {

        validateFile(file);
        validateZipContents(file);

    }

    private void validateFile(MultipartFile file) {

        if (file == null) {
            throw new InvalidZipException("No file uploaded.");
        }

        if (file.isEmpty()) {
            throw new InvalidZipException("Uploaded file is empty.");
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null || fileName.isBlank()) {
            throw new InvalidZipException("Invalid file name.");
        }

        fileName = fileName.trim().toLowerCase();

        if (!fileName.endsWith(".zip")) {
            throw new InvalidZipException("Only ZIP files are allowed.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidZipException("Maximum upload size is 500 MB.");
        }
    }

    private void validateZipContents(MultipartFile file) {

        try (ZipInputStream zipInputStream =
                     new ZipInputStream(file.getInputStream())) {

            ZipEntry firstEntry = zipInputStream.getNextEntry();

            if (firstEntry == null) {
                throw new InvalidZipException("ZIP archive is empty.");
            }

        } catch (IOException e) {
            throw new InvalidZipException("Invalid or corrupted ZIP archive.");
        }

    }
}