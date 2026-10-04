package com.anvith.archmorph.upload;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.InvalidZipException;
import com.anvith.archmorph.upload.service.ExtractionReport;
import com.anvith.archmorph.upload.service.SecureZipExtractor;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureZipExtractorTest {

    @TempDir
    Path temp;

    private ArchMorphProperties properties;
    private SecureZipExtractor extractor;
    private Path destination;

    @BeforeEach
    void setUp() {
        properties = new ArchMorphProperties();
        extractor = new SecureZipExtractor(properties);
        destination = temp.resolve("out");
    }

    @Test
    void extractsProjectAndSkipsGeneratedDirectoriesOutsideSrc() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("demo/pom.xml", "<project/>");
        entries.put("demo/src/main/java/com/x/App.java", "class App {}");
        entries.put("demo/src/main/java/com/x/build/Builder.java", "class Builder {}");
        entries.put("demo/target/classes/App.class", "binary");
        entries.put("demo/.git/config", "secret");
        entries.put("demo/node_modules/x/index.js", "js");
        entries.put("demo/.DS_Store", "junk");
        Path zip = zip(entries);

        ExtractionReport report = extractor.extract(zip, destination);

        assertThat(destination.resolve("demo/src/main/java/com/x/App.java")).exists();
        assertThat(destination.resolve("demo/src/main/java/com/x/build/Builder.java")).as("'build' inside src is kept").exists();
        assertThat(destination.resolve("demo/target")).doesNotExist();
        assertThat(destination.resolve("demo/.git")).doesNotExist();
        assertThat(destination.resolve("demo/node_modules")).doesNotExist();
        assertThat(report.extractedFiles()).isEqualTo(3);
        assertThat(report.skippedEntries()).isEqualTo(4);
        assertThat(report.skippedDirectories()).contains("target", ".git", "node_modules");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil.txt", "demo/../../evil.txt", "/etc/passwd", "C:\\Windows\\evil.txt",
            "..\\evil.txt", "demo\\..\\..\\evil.txt", "//server/share/evil.txt"})
    void rejectsPathTraversalAndAbsolutePaths(String name) throws IOException {
        Path zip = zip(Map.of(name, "pwned"));

        assertThatThrownBy(() -> extractor.extract(zip, destination))
                .isInstanceOf(InvalidZipException.class)
                .satisfies(e -> assertThat(((InvalidZipException) e).getErrorCode()).isEqualTo(ErrorCode.UNSAFE_ARCHIVE_ENTRY));
        assertThat(temp.resolve("evil.txt")).doesNotExist();
    }

    @Test
    void rejectsSymbolicLinks() throws IOException {
        Path zip = temp.resolve("link.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
            ZipArchiveEntry link = new ZipArchiveEntry("demo/link");
            link.setUnixMode(0120777);
            out.putArchiveEntry(link);
            out.write("/etc/passwd".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        assertThatThrownBy(() -> extractor.extract(zip, destination)).hasMessageContaining("symbolic link");
    }

    @Test
    void enforcesEntryCountLimit() throws IOException {
        properties.getUpload().setMaxEntryCount(3);
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            entries.put("demo/f" + i + ".txt", "x");
        }
        assertThatThrownBy(() -> extractor.extract(zip(entries), destination))
                .satisfies(e -> assertThat(((InvalidZipException) e).getErrorCode()).isEqualTo(ErrorCode.ARCHIVE_LIMIT_EXCEEDED));
    }

    @Test
    void enforcesSingleEntrySizeOnActualBytes() throws IOException {
        properties.getUpload().setMaxSingleEntrySize(DataSize.ofKilobytes(1));
        assertThatThrownBy(() -> extractor.extract(zip(Map.of("demo/big.txt", "x".repeat(4096))), destination))
                .hasMessageContaining("single-file size");
    }

    @Test
    void enforcesTotalUncompressedSize() throws IOException {
        properties.getUpload().setMaxUncompressedSize(DataSize.ofKilobytes(10));
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            entries.put("demo/f" + i + ".txt", "y".repeat(4000));
        }
        assertThatThrownBy(() -> extractor.extract(zip(entries), destination)).hasMessageContaining("uncompressed size");
    }

    @Test
    void rejectsCompressionBombs() throws IOException {
        Path zip = temp.resolve("bomb.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
            out.putArchiveEntry(new ZipArchiveEntry("demo/zeros.bin"));
            byte[] zeros = new byte[1024 * 1024];
            for (int i = 0; i < 20; i++) {
                out.write(zeros);
            }
            out.closeArchiveEntry();
        }
        assertThatThrownBy(() -> extractor.extract(zip, destination)).hasMessageContaining("compression ratio");
    }

    @Test
    void rejectsDuplicateEntries() throws IOException {
        Path zip = temp.resolve("dup.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
            for (String content : new String[]{"first", "second"}) {
                out.putArchiveEntry(new ZipArchiveEntry("demo/a.txt"));
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.closeArchiveEntry();
            }
        }
        assertThatThrownBy(() -> extractor.extract(zip, destination)).hasMessageContaining("duplicate");
    }

    @Test
    void rejectsArchivesWithoutFilesAndGarbage() throws IOException {
        assertThatThrownBy(() -> extractor.extract(zip(Map.of()), destination)).isInstanceOf(InvalidZipException.class);
        Path garbage = temp.resolve("garbage.zip");
        Files.writeString(garbage, "this is not a zip");
        assertThatThrownBy(() -> extractor.extract(garbage, destination)).isInstanceOf(InvalidZipException.class);
    }

    private Path zip(Map<String, String> entries) throws IOException {
        Path zip = Files.createTempFile(temp, "archive", ".zip");
        try (OutputStream file = Files.newOutputStream(zip);
             ZipArchiveOutputStream out = new ZipArchiveOutputStream(file)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putArchiveEntry(new ZipArchiveEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeArchiveEntry();
            }
        }
        return zip;
    }
}
