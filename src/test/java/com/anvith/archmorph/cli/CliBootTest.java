package com.anvith.archmorph.cli;

import com.anvith.archmorph.ArchMorphApplicationCliAccess;
import com.anvith.archmorph.support.ApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the CLI exactly as {@code java -jar archmorph.jar <command>} does (non-web context, real wiring). */
class CliBootTest {

    @TempDir
    Path temp;

    @Test
    void helpBootsTheCliContext() throws Exception {
        assertThat(ArchMorphApplicationCliAccess.runCli("help")).isZero();
    }

    @Test
    void planRunsEndToEndAndWritesReports() throws Exception {
        Path zip = Files.write(temp.resolve("simple-layered.zip"), ApiClient.zipFixture("simple-layered"));
        Path reports = temp.resolve("reports");
        int exit = ArchMorphApplicationCliAccess.runCli("plan", zip.toString(), "--report-dir", reports.toString());
        assertThat(exit).isZero();
        assertThat(reports.resolve("transformation-plan.json")).exists();
        assertThat(Files.readString(reports.resolve("analysis.md"))).contains("simple-layered");
    }
}
