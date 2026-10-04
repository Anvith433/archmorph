package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.InvalidZipException;
import com.anvith.archmorph.common.exception.ProjectExtractionException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Hardened ZIP extraction.
 *
 * <ul>
 *     <li>Zip Slip: every normalised target must stay inside the destination.</li>
 *     <li>Absolute paths, drive letters, NUL bytes and ".." segments are rejected.</li>
 *     <li>Symbolic-link entries are rejected.</li>
 *     <li>Limits on entry count, per-entry size, total uncompressed size and
 *         compression ratio are enforced on the bytes actually written
 *         (declared sizes in the archive are not trusted).</li>
 *     <li>Generated/IDE directories (target/, .git/, node_modules/, ...) are skipped
 *         outside {@code src/}.</li>
 *     <li>Files are created with CREATE_NEW: duplicate entries cannot overwrite.</li>
 * </ul>
 */
@Service
public class SecureZipExtractor implements ZipExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SecureZipExtractor.class);
    private static final long RATIO_CHECK_MIN_BYTES = 1024 * 1024;

    private final ArchMorphProperties properties;

    public SecureZipExtractor(ArchMorphProperties properties) {
        this.properties = properties;
    }

    @Override
    public ExtractionReport extract(Path archive, Path destination) {
        ArchMorphProperties.Upload limits = properties.getUpload();
        Path root = destination.toAbsolutePath().normalize();
        Set<String> ignoredDirs = lowerCase(limits.getIgnoredDirectories());
        Set<String> ignoredFiles = lowerCase(limits.getIgnoredFiles());

        int entryCount = 0;
        int extracted = 0;
        int skipped = 0;
        long totalBytes = 0;
        Set<String> skippedDirs = new TreeSet<>();

        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            Files.createDirectories(root);
            Enumeration<ZipArchiveEntry> entries = zip.getEntriesInPhysicalOrder();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > limits.getMaxEntryCount()) {
                    throw limit("The archive contains more than " + limits.getMaxEntryCount() + " entries.");
                }

                String name = validateName(entry.getName());
                if (entry.isUnixSymlink()) {
                    throw unsafe("The archive contains a symbolic link, which is not allowed.");
                }

                String ignoredSegment = ignoredSegment(name, ignoredDirs);
                String fileName = name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (ignoredSegment != null || ignoredFiles.contains(fileName)) {
                    skipped++;
                    if (ignoredSegment != null) {
                        skippedDirs.add(ignoredSegment);
                    }
                    continue;
                }

                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root) || target.equals(root)) {
                    throw unsafe("The archive contains an entry outside the extraction directory.");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                Files.createDirectories(target.getParent());
                long written = copyEntry(zip, entry, target, limits, limits.getMaxUncompressedSize().toBytes() - totalBytes);
                totalBytes += written;
                extracted++;
            }
        } catch (InvalidZipException e) {
            throw e;
        } catch (FileAlreadyExistsException e) {
            throw unsafe("The archive contains duplicate entries.");
        } catch (IOException e) {
            throw new InvalidZipException("The archive is corrupted or not a valid ZIP file.");
        } catch (RuntimeException e) {
            throw new ProjectExtractionException("Unable to extract uploaded project.", e);
        }

        if (extracted == 0) {
            throw new InvalidZipException("The archive does not contain any files.");
        }
        log.info("Extracted {} files ({} bytes), skipped {} generated/IDE entries", extracted, totalBytes, skipped);
        return new ExtractionReport(extracted, skipped, totalBytes, List.copyOf(skippedDirs));
    }

    private long copyEntry(ZipFile zip, ZipArchiveEntry entry, Path target,
                           ArchMorphProperties.Upload limits, long remainingTotal) throws IOException {
        long maxEntry = limits.getMaxSingleEntrySize().toBytes();
        long compressed = Math.max(entry.getCompressedSize(), 1);
        long written = 0;
        byte[] buffer = new byte[32 * 1024];
        try (InputStream in = zip.getInputStream(entry);
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > maxEntry) {
                    throw limit("An archive entry exceeds the maximum single-file size of "
                            + (maxEntry / (1024 * 1024)) + " MB.");
                }
                if (written > remainingTotal) {
                    throw limit("The archive exceeds the maximum uncompressed size of "
                            + (limits.getMaxUncompressedSize().toBytes() / (1024 * 1024)) + " MB.");
                }
                if (written > RATIO_CHECK_MIN_BYTES && entry.getCompressedSize() > 0
                        && written / compressed > limits.getMaxCompressionRatio()) {
                    throw limit("The archive has a suspicious compression ratio (possible zip bomb).");
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    /** Validate and normalise an entry name to a relative '/'-separated path. */
    static String validateName(String rawName) {
        if (rawName == null || rawName.isEmpty() || rawName.indexOf('\0') >= 0) {
            throw unsafe("The archive contains an entry with an invalid name.");
        }
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*") || name.startsWith("//")) {
            throw unsafe("The archive contains an absolute path entry.");
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                throw unsafe("The archive contains a path-traversal entry ('..').");
            }
        }
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.length() > 1024) {
            throw unsafe("The archive contains an entry with an excessively long name.");
        }
        return name;
    }

    /** Ignored directory segment found before any "src" segment, or null. */
    static String ignoredSegment(String name, Set<String> ignored) {
        String[] segments = name.split("/");
        int directorySegments = name.endsWith("/") ? segments.length : segments.length - 1;
        for (int i = 0; i < directorySegments; i++) {
            String segment = segments[i].toLowerCase(Locale.ROOT);
            if (segment.equals("src")) {
                return null;
            }
            if (ignored.contains(segment)) {
                return segment;
            }
        }
        return null;
    }

    private static Set<String> lowerCase(List<String> values) {
        Set<String> result = new TreeSet<>();
        values.forEach(v -> result.add(v.toLowerCase(Locale.ROOT)));
        return result;
    }

    private static InvalidZipException unsafe(String message) {
        return new InvalidZipException(ErrorCode.UNSAFE_ARCHIVE_ENTRY, message,
                "Re-create the archive from your project directory using a standard ZIP tool.");
    }

    private static InvalidZipException limit(String message) {
        return new InvalidZipException(ErrorCode.ARCHIVE_LIMIT_EXCEEDED, message,
                "Remove generated directories such as target/ and node_modules/ and upload again.");
    }

    static List<String> describe(List<String> list) {
        return new ArrayList<>(list);
    }
}
