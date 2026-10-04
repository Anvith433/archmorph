package com.anvith.archmorph.analysis.transformation.build;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Adds Spring Modulith's boundary verification to a transformed Maven project, so the project's own build keeps
 * the module boundaries checked: the {@code spring-modulith-bom} import, the {@code spring-modulith-starter-test}
 * test dependency and a {@code ModularityTests} class calling {@code ApplicationModules.of(App.class).verify()}.
 *
 * <p>The pom is edited by inserting text at element positions, never by re-serialising it, so its formatting
 * and comments are preserved. Nothing is added twice, existing files are never overwritten, and when the Spring
 * Boot version cannot be mapped to a Spring Modulith release (and none is configured) nothing is changed.</p>
 */
@Component
public class ModulithSetup {

    public static final String TEST_CLASS = "ModularityTests";

    /** Spring Boot minor line → latest Spring Modulith release for it (from Spring Modulith's compatibility matrix). */
    private static final Map<String, String> MODULITH_FOR_BOOT = new TreeMap<>(Map.of(
            "3.1", "1.0.8",
            "3.2", "1.1.12",
            "3.3", "1.2.13",
            "3.4", "1.3.12",
            "3.5", "1.4.13",
            "4.0", "2.0.8",
            "4.1", "2.1.1"));

    /**
     * @param pomUpdated        the pom now declares the Spring Modulith test dependency
     * @param testFile          project-relative path of the generated test, or null when none was written
     * @param modulithVersion   the Spring Modulith version used, or null
     * @param warnings          what was skipped and why
     */
    public record Result(boolean pomUpdated, String testFile, String modulithVersion, List<String> warnings) {
    }

    public static Optional<String> modulithVersionFor(String springBootVersion) {
        if (springBootVersion == null) {
            return Optional.empty();
        }
        String[] parts = springBootVersion.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        return Optional.ofNullable(MODULITH_FOR_BOOT.get(parts[0] + "." + parts[1]));
    }

    /** The Spring Boot version from the parent, a {@code spring-boot-dependencies} import or a property. */
    public static Optional<String> springBootVersion(PomDocument pom) {
        Optional<String> parentArtifact = pom.value("project/parent/artifactId");
        if (parentArtifact.isPresent() && parentArtifact.get().startsWith("spring-boot")) {
            Optional<String> version = pom.value("project/parent/version").map(v -> resolve(pom, v));
            if (version.isPresent()) {
                return version;
            }
        }
        for (PomDocument.Element dependency : pom.all("project/dependencyManagement/dependencies/dependency")) {
            if (pom.childValue(dependency, "artifactId").filter("spring-boot-dependencies"::equals).isPresent()) {
                return pom.childValue(dependency, "version").map(v -> resolve(pom, v));
            }
        }
        return pom.value("project/properties/spring-boot.version");
    }

    private static String resolve(PomDocument pom, String value) {
        if (value.startsWith("${") && value.endsWith("}")) {
            return pom.value("project/properties/" + value.substring(2, value.length() - 1)).orElse(value);
        }
        return value;
    }

    /**
     * @param applicationClass fully-qualified name of the {@code @SpringBootApplication} class
     * @param configuredVersion Spring Modulith version to use; blank to derive it from the Spring Boot version
     */
    public Result apply(Path projectRoot, String applicationClass, String configuredVersion) throws IOException {
        return apply(projectRoot, "", applicationClass, configuredVersion);
    }

    /**
     * @param moduleDirectory project-relative directory of the Maven module holding the application class ("" for
     *                        a single-module project); its pom receives the dependencies and its tests the new test
     */
    public Result apply(Path projectRoot, String moduleDirectory, String applicationClass, String configuredVersion) throws IOException {
        List<String> warnings = new ArrayList<>();
        Path moduleRoot = moduleDirectory == null || moduleDirectory.isEmpty() ? projectRoot : projectRoot.resolve(moduleDirectory).normalize();
        if (!moduleRoot.startsWith(projectRoot)) {
            return new Result(false, null, null, List.of("The application module lies outside the project; Spring Modulith was not added."));
        }
        if (!Files.isRegularFile(moduleRoot.resolve("pom.xml"))
                && (Files.isRegularFile(projectRoot.resolve("build.gradle")) || Files.isRegularFile(projectRoot.resolve("build.gradle.kts")))) {
            return new Result(false, null, null, List.of("Gradle build: build scripts are not edited. Add "
                    + "testImplementation(platform(\"org.springframework.modulith:spring-modulith-bom:<version>\")) and "
                    + "testImplementation(\"org.springframework.modulith:spring-modulith-starter-test\") to enable the "
                    + "ModularityTests shown in MODULES.md."));
        }
        Path pomFile = moduleRoot.resolve("pom.xml");
        if (!Files.isRegularFile(pomFile)) {
            return new Result(false, null, null, List.of("No pom.xml for the application module; Spring Modulith was not added."));
        }
        if (applicationClass == null) {
            return new Result(false, null, null, List.of("No @SpringBootApplication class found; Spring Modulith was not added."));
        }
        String original = Files.readString(pomFile, StandardCharsets.UTF_8);
        PomDocument pom = PomDocument.parse(original);

        Optional<String> bootVersion = springBootVersion(pom);
        if (bootVersion.isEmpty() && !moduleRoot.equals(projectRoot) && Files.isRegularFile(projectRoot.resolve("pom.xml"))) {
            bootVersion = springBootVersion(PomDocument.parse(Files.readString(projectRoot.resolve("pom.xml"), StandardCharsets.UTF_8)));
        }
        String version = configuredVersion != null && !configuredVersion.isBlank() ? configuredVersion.trim()
                : bootVersion.flatMap(ModulithSetup::modulithVersionFor).orElse(null);
        boolean alreadyDeclared = original.contains("spring-modulith");
        if (version == null && !alreadyDeclared) {
            return new Result(false, null, null, List.of("The Spring Boot version (" + bootVersion.orElse("unknown")
                    + ") has no known Spring Modulith release; set archmorph.transformation.modulith-version to add it."));
        }

        boolean pomUpdated = false;
        if (alreadyDeclared) {
            warnings.add("The pom already mentions Spring Modulith; it was left unchanged.");
        } else {
            Files.writeString(pomFile, addDependencies(pom, version), StandardCharsets.UTF_8);
            pomUpdated = true;
        }
        if (!original.contains("spring-boot-starter-test") && !original.contains("junit-jupiter")) {
            warnings.add("The project declares no JUnit 5 test dependency; add spring-boot-starter-test to run ModularityTests.");
        }

        String testFile = writeTest(projectRoot, moduleRoot, applicationClass, warnings);
        return new Result(pomUpdated || alreadyDeclared, testFile, version, warnings);
    }

    // ================================================================== pom

    static String addDependencies(PomDocument pom, String version) {
        String nl = pom.lineSeparator();
        String unit = pom.first("project/modelVersion").map(e -> pom.indentationAt(e.openStart())).filter(s -> !s.isEmpty()).orElse("    ");
        record Insertion(int offset, String text) {
        }
        List<Insertion> insertions = new ArrayList<>();

        String bom = dependency(unit.repeat(3), unit, nl, "org.springframework.modulith", "spring-modulith-bom", version,
                "<type>pom</type>", "<scope>import</scope>");
        Optional<PomDocument.Element> managed = pom.first("project/dependencyManagement/dependencies");
        Optional<PomDocument.Element> management = pom.first("project/dependencyManagement");
        if (managed.isPresent() && !managed.get().selfClosing()) {
            insertions.add(new Insertion(lineStart(pom, managed.get().closeStart()), bom));
        } else if (management.isPresent() && !management.get().selfClosing()) {
            insertions.add(new Insertion(lineStart(pom, management.get().closeStart()),
                    unit.repeat(2) + "<dependencies>" + nl + bom + unit.repeat(2) + "</dependencies>" + nl));
        } else {
            int anchor = pom.first("project/dependencies").map(PomDocument.Element::openStart)
                    .or(() -> pom.first("project").map(PomDocument.Element::closeStart)).orElse(pom.text().length());
            insertions.add(new Insertion(lineStart(pom, anchor),
                    unit + "<dependencyManagement>" + nl + unit.repeat(2) + "<dependencies>" + nl + bom
                            + unit.repeat(2) + "</dependencies>" + nl + unit + "</dependencyManagement>" + nl));
        }

        String test = dependency(unit.repeat(2), unit, nl, "org.springframework.modulith", "spring-modulith-starter-test", null,
                "<scope>test</scope>");
        Optional<PomDocument.Element> dependencies = pom.first("project/dependencies");
        if (dependencies.isPresent() && !dependencies.get().selfClosing()) {
            insertions.add(new Insertion(lineStart(pom, dependencies.get().closeStart()), test));
        } else {
            int anchor = pom.first("project").map(PomDocument.Element::closeStart).orElse(pom.text().length());
            insertions.add(new Insertion(lineStart(pom, anchor),
                    unit + "<dependencies>" + nl + test + unit + "</dependencies>" + nl));
        }

        StringBuilder result = new StringBuilder(pom.text());
        insertions.stream().sorted(Comparator.comparingInt(Insertion::offset).reversed())
                .forEach(insertion -> result.insert(insertion.offset(), insertion.text()));
        return result.toString();
    }

    private static String dependency(String indent, String unit, String nl, String groupId, String artifactId, String version,
                                     String... extra) {
        StringBuilder block = new StringBuilder();
        block.append(indent).append("<dependency>").append(nl);
        block.append(indent).append(unit).append("<groupId>").append(groupId).append("</groupId>").append(nl);
        block.append(indent).append(unit).append("<artifactId>").append(artifactId).append("</artifactId>").append(nl);
        if (version != null) {
            block.append(indent).append(unit).append("<version>").append(version).append("</version>").append(nl);
        }
        for (String line : extra) {
            block.append(indent).append(unit).append(line).append(nl);
        }
        block.append(indent).append("</dependency>").append(nl);
        return block.toString();
    }

    /** Start of the line holding {@code offset} when only whitespace precedes it, otherwise the offset itself. */
    private static int lineStart(PomDocument pom, int offset) {
        String text = pom.text();
        int start = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        return text.substring(start, offset).isBlank() ? start : offset;
    }

    // ================================================================== test

    private static String writeTest(Path projectRoot, Path moduleRoot, String applicationClass, List<String> warnings) throws IOException {
        int dot = applicationClass.lastIndexOf('.');
        String pkg = dot < 0 ? "" : applicationClass.substring(0, dot);
        String simpleName = applicationClass.substring(dot + 1);
        Path directory = moduleRoot.resolve("src/test/java");
        if (!pkg.isEmpty()) {
            directory = directory.resolve(pkg.replace('.', '/'));
        }
        String className = TEST_CLASS;
        Path file = directory.resolve(className + ".java");
        if (Files.exists(file)) {
            className = "ArchMorph" + TEST_CLASS;
            file = directory.resolve(className + ".java");
            if (Files.exists(file)) {
                warnings.add("A modularity test already exists; no test class was written.");
                return null;
            }
        }
        String nl = "\n";
        String source = (pkg.isEmpty() ? "" : "package " + pkg + ";" + nl + nl)
                + "import org.junit.jupiter.api.Test;" + nl
                + "import org.springframework.modulith.core.ApplicationModules;" + nl + nl
                + "/**" + nl
                + " * Verifies the module structure: modules only use each other's API packages and do not depend on" + nl
                + " * each other in cycles. Generated by ArchMorph; see MODULES.md." + nl
                + " */" + nl
                + "class " + className + " {" + nl + nl
                + "    @Test" + nl
                + "    void verifiesModularStructure() {" + nl
                + "        ApplicationModules.of(" + simpleName + ".class).verify();" + nl
                + "    }" + nl
                + "}" + nl;
        Files.createDirectories(directory);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return projectRoot.relativize(file).toString().replace('\\', '/');
    }
}
