package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ArchiveStorageException;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.InvalidZipException;
import com.anvith.archmorph.workspace.ProjectWorkspace;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Stores the upload as {@code input/archive.zip}. The client-supplied file
 * name is never used for the path; the size cap is enforced while copying.
 */
@Service
public class ArchiveStorageServiceImpl implements ArchiveStorageService {

    private final ArchMorphProperties properties;

    public ArchiveStorageServiceImpl(ArchMorphProperties properties) {
        this.properties = properties;
    }

    @Override
    public Path saveArchive(ProjectWorkspace workspace, MultipartFile file) {
        Path target = workspace.archive();
        long max = properties.getUpload().getMaxArchiveSize().toBytes();
        try (InputStream in = file.getInputStream();
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > max) {
                    throw new InvalidZipException(ErrorCode.ARCHIVE_TOO_LARGE,
                            "The archive exceeds the maximum upload size.",
                            "Remove generated directories such as target/ and node_modules/ and upload again.");
                }
                out.write(buffer, 0, read);
            }
            return target;
        } catch (IOException e) {
            throw new ArchiveStorageException("Unable to save uploaded archive.", e);
        }
    }
}
