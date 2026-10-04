package com.anvith.archmorph.analysis.validation.build;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Refuses to run a build for projects that declare Maven plugins whose purpose is to execute
 * arbitrary commands. Compiling any project still runs the code of its declared plugins and
 * annotation processors; this guard only removes the plugins that are explicitly command runners.
 */
@Component
public class BuildPluginGuard {

    private static final List<String> DENYLIST = List.of(
            "exec-maven-plugin", "maven-antrun-plugin", "gmavenplus-plugin", "groovy-maven-plugin",
            "frontend-maven-plugin", "maven-invoker-plugin", "script-maven-plugin", "maven-scripting-plugin",
            "docker-maven-plugin", "jib-maven-plugin", "maven-deploy-plugin", "maven-release-plugin",
            "maven-scm-plugin", "wagon-maven-plugin", "ant-maven-plugin", "nodejs-maven-plugin");

    /** @return the denied plugin names found in any pom.xml below {@code projectDir}, or an empty list */
    public List<String> deniedPlugins(Path projectDir) {
        List<String> found = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(projectDir, 8)) {
            for (Path pom : (Iterable<Path>) stream.filter(p -> p.getFileName().toString().equals("pom.xml")
                    && Files.isRegularFile(p) && !Files.isSymbolicLink(p))::iterator) {
                String text = Files.readString(pom).toLowerCase(Locale.ROOT);
                for (String plugin : DENYLIST) {
                    if (text.contains(plugin) && !found.contains(plugin)) {
                        found.add(plugin);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            found.add("unreadable pom.xml");
        }
        return found;
    }

    /** True when a module path could leave the project directory. */
    public boolean hasEscapingModule(Path projectDir) {
        try (Stream<Path> stream = Files.walk(projectDir, 8)) {
            for (Path pom : (Iterable<Path>) stream.filter(p -> p.getFileName().toString().equals("pom.xml")
                    && Files.isRegularFile(p))::iterator) {
                String text = Files.readString(pom);
                if (text.matches("(?s).*<module>\\s*(\\.\\./|/|[A-Za-z]:).*")) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException e) {
            return true;
        }
        return false;
    }
}
