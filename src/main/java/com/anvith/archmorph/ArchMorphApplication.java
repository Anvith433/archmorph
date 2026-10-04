package com.anvith.archmorph;

import com.anvith.archmorph.cli.CliRunner;
import com.anvith.archmorph.cli.PasswordHashCommand;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * ArchMorph entry point. Started without arguments (or with Spring options) it runs the web server;
 * started with {@code analyze|plan|transform} it runs the command-line interface and exits, without
 * starting a web server.
 */
@SpringBootApplication
@EnableConfigurationProperties(ArchMorphProperties.class)
public class ArchMorphApplication {

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("hash-password")) {
            System.exit(PasswordHashCommand.run(args));
        }
        if (CliRunner.isCliInvocation(args)) {
            System.exit(runCli(args));
        }
        SpringApplication.run(ArchMorphApplication.class, args);
    }

    /** Runs one CLI command in a non-web context with a temporary workspace; returns the exit code. */
    static int runCli(String[] args) throws IOException {
        Path workspace = Files.createTempDirectory("archmorph-cli-");
        try {
            SpringApplication application = new SpringApplication(ArchMorphApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setBannerMode(Banner.Mode.OFF);
            application.setDefaultProperties(Map.of(
                    "archmorph.cli.enabled", "true",
                    "archmorph.workspace.root", workspace.toString(),
                    "spring.main.log-startup-info", "false",
                    "logging.level.root", "WARN",
                    "archmorph.validation.build.mode", "COMPILE"));
            return SpringApplication.exit(application.run(args));
        } finally {
            deleteQuietly(workspace);
        }
    }

    private static void deleteQuietly(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException | UncheckedIOException ignored) {
            // best effort: the directory is in the system temp folder
        }
    }

}
