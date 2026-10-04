package com.anvith.archmorph.cli;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.project.ProjectWorkflow;
import com.anvith.archmorph.report.ReportService;
import com.anvith.archmorph.support.ApiClient;
import com.anvith.archmorph.support.ArchMorphTest;
import com.anvith.archmorph.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

@ArchMorphTest
class CliRunnerTest {

    @Autowired
    ProjectWorkflow workflow;

    @Autowired
    WorkspaceManager workspaceManager;

    @Autowired
    ReportService reports;

    @Autowired
    ArchMorphProperties properties;

    @TempDir
    Path temp;

    @Test
    void transformCommandWritesProjectAndReports() throws Exception {
        Path zip = Files.write(temp.resolve("shop.zip"), ApiClient.zipFixture("spring-layered"));
        Path output = temp.resolve("out.zip");
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        CliRunner cli = new CliRunner(workflow, workspaceManager, reports, properties, console);

        int exit = cli.execute(new String[]{"transform", zip.toString(), "--output", output.toString(),
                "--report-dir", temp.resolve("reports").toString(), "--no-build"});

        String text = console.toString(StandardCharsets.UTF_8);
        assertThat(exit).as(text).isZero();
        assertThat(text).contains("Candidate modules", "Plan (MODULAR_MONOLITH)", "Validation:");
        try (ZipFile result = new ZipFile(output.toFile())) {
            assertThat(result.getEntry("src/main/java/com/demo/user/UserService.java")).isNotNull();
            assertThat(result.getEntry("src/main/java/com/demo/user/controller/UserController.java")).isNotNull();
            assertThat(result.getEntry("MODULES.md")).isNotNull();
        }

        Path byDomain = temp.resolve("by-domain.zip");
        assertThat(cli.execute(new String[]{"transform", zip.toString(), "--output", byDomain.toString(),
                "--strategy", "modular-by-domain", "--no-build"})).isZero();
        try (ZipFile result = new ZipFile(byDomain.toFile())) {
            assertThat(result.getEntry("src/main/java/com/demo/modules/user/service/UserService.java")).isNotNull();
        }
        assertThat(cli.execute(new String[]{"plan", zip.toString(), "--strategy", "microservices"})).isEqualTo(2);
        assertThat(temp.resolve("reports/transformation-plan.json")).exists();
        assertThat(temp.resolve("reports/validation-report.md")).exists();
        assertThat(zip).as("input archive untouched").exists();
    }

    @Test
    void usageErrorsReturnExitCodeTwo() {
        CliRunner cli = new CliRunner(workflow, workspaceManager, reports, properties, new ByteArrayOutputStream());
        assertThat(cli.execute(new String[]{})).isEqualTo(2);
        assertThat(cli.execute(new String[]{"transform", "missing.zip"})).isEqualTo(2);
        assertThat(cli.execute(new String[]{"explode", "x.zip"})).isEqualTo(2);
        assertThat(CliRunner.isCliInvocation(new String[]{"analyze", "x.zip"})).isTrue();
        assertThat(CliRunner.isCliInvocation(new String[]{"--server.port=9000"})).isFalse();
    }
}
