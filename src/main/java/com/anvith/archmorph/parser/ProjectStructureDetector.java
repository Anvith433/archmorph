package com.anvith.archmorph.parser;

import com.anvith.archmorph.common.exception.InvalidProjectStructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Detects the project root, build tool, source roots and features of an
 * extracted project. The uploaded pom.xml is parsed with DTDs and external
 * entities disabled (it is untrusted input).
 */
@Service
public class ProjectStructureDetector {

    private static final Logger log = LoggerFactory.getLogger(ProjectStructureDetector.class);

    private static final List<String> UNUSUAL_PLUGINS = List.of(
            "build-helper-maven-plugin", "maven-antrun-plugin", "exec-maven-plugin",
            "maven-shade-plugin", "jaxb2-maven-plugin", "openapi-generator-maven-plugin",
            "protobuf-maven-plugin", "avro-maven-plugin", "jooq-codegen-maven");

    /** Backwards-compatible: just the root directory. */
    public Path detectProjectRoot(Path originalDirectory) {
        return detect(originalDirectory).projectRoot();
    }

    public ProjectStructure detect(Path extractedDirectory) {
        Path root = findShallowest(extractedDirectory, "pom.xml")
                .orElse(null);

        if (root == null) {
            if (findShallowest(extractedDirectory, "build.gradle").isPresent()
                    || findShallowest(extractedDirectory, "build.gradle.kts").isPresent()) {
                throw new InvalidProjectStructureException(
                        "Gradle projects are not supported yet. This version of ArchMorph analyses Maven projects.");
            }
            throw new InvalidProjectStructureException("No Maven project found. pom.xml is missing.");
        }

        List<String> warnings = new ArrayList<>();
        Document pom = parsePom(root.resolve("pom.xml"), warnings);

        String groupId = pom == null ? null : directChildText(pom.getDocumentElement(), "groupId");
        String artifactId = pom == null ? null : directChildText(pom.getDocumentElement(), "artifactId");
        List<String> modules = pom == null ? List.of() : moduleNames(pom);
        String pomText = readQuietly(root.resolve("pom.xml"));

        boolean springBoot = pomText.contains("spring-boot");
        boolean annotationProcessors = pomText.contains("annotationProcessorPaths")
                || pomText.contains("mapstruct-processor") || pomText.contains("querydsl-apt");

        List<Path> mainRoots = new ArrayList<>();
        List<Path> testRoots = new ArrayList<>();
        List<Path> resourceRoots = new ArrayList<>();
        addRoots(root, mainRoots, testRoots, resourceRoots);
        for (String module : modules) {
            Path moduleDir = root.resolve(module).normalize();
            if (moduleDir.startsWith(root) && Files.isDirectory(moduleDir)) {
                addRoots(moduleDir, mainRoots, testRoots, resourceRoots);
            }
        }

        boolean kotlin = containsExtension(root, ".kt");
        boolean groovyOrScala = containsExtension(root, ".groovy") || containsExtension(root, ".scala");

        if (!modules.isEmpty()) {
            warnings.add("Multi-module Maven build (" + modules.size()
                    + " modules). Classes are moved within their own Maven module only; review cross-module packages.");
        }
        if (kotlin) {
            warnings.add("Kotlin sources detected. Kotlin files are preserved but NOT rewritten; references from Kotlin to moved Java classes require manual review.");
        }
        if (groovyOrScala) {
            warnings.add("Groovy/Scala sources detected. They are preserved but not rewritten.");
        }
        if (annotationProcessors) {
            warnings.add("Annotation processors are configured. Generated sources may reference old packages until they are regenerated.");
        }
        for (String plugin : UNUSUAL_PLUGINS) {
            if (pomText.contains(plugin)) {
                warnings.add("Build plugin '" + plugin + "' detected. It may rely on source locations or generate code; review after transformation.");
            }
        }
        if (!springBoot) {
            warnings.add("No Spring Boot dependency found. Classification relies more on naming and package conventions.");
        }

        if (mainRoots.isEmpty()) {
            throw new InvalidProjectStructureException("src/main/java directory not found.");
        }

        log.debug("Detected Maven project with {} main and {} test source roots", mainRoots.size(), testRoots.size());

        return new ProjectStructure(root, ProjectStructure.BuildTool.MAVEN, groupId, artifactId, springBoot,
                !modules.isEmpty(), List.copyOf(modules), List.copyOf(mainRoots), List.copyOf(testRoots),
                List.copyOf(resourceRoots), kotlin, groovyOrScala, annotationProcessors, List.copyOf(warnings));
    }

    private void addRoots(Path base, List<Path> main, List<Path> test, List<Path> resources) {
        Path mainJava = base.resolve("src/main/java");
        Path testJava = base.resolve("src/test/java");
        if (Files.isDirectory(mainJava)) {
            main.add(mainJava);
        }
        if (Files.isDirectory(testJava)) {
            test.add(testJava);
        }
        for (String res : List.of("src/main/resources", "src/test/resources")) {
            if (Files.isDirectory(base.resolve(res))) {
                resources.add(base.resolve(res));
            }
        }
    }

    private Optional<Path> findShallowest(Path directory, String fileName) {
        try (Stream<Path> stream = Files.walk(directory, 6)) {
            return stream
                    .filter(path -> path.getFileName() != null && path.getFileName().toString().equals(fileName))
                    .filter(Files::isRegularFile)
                    .min(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString))
                    .map(Path::getParent);
        } catch (IOException e) {
            throw new InvalidProjectStructureException("Unable to inspect extracted project.", e);
        }
    }

    private boolean containsExtension(Path root, String extension) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.anyMatch(p -> p.toString().endsWith(extension) && Files.isRegularFile(p));
        } catch (IOException e) {
            return false;
        }
    }

    private Document parsePom(Path pom, List<String> warnings) {
        try (InputStream in = Files.newInputStream(pom)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(in);
        } catch (Exception e) {
            warnings.add("pom.xml could not be parsed as XML; build metadata is incomplete.");
            return null;
        }
    }

    private List<String> moduleNames(Document pom) {
        List<String> modules = new ArrayList<>();
        Element project = pom.getDocumentElement();
        NodeList children = project.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && "modules".equals(element.getTagName())) {
                NodeList moduleNodes = element.getElementsByTagName("module");
                for (int j = 0; j < moduleNodes.getLength(); j++) {
                    String name = moduleNodes.item(j).getTextContent().trim();
                    if (!name.isEmpty() && !name.contains("..")) {
                        modules.add(name);
                    }
                }
            }
        }
        return modules;
    }

    private String directChildText(Element element, String tag) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && tag.equals(child.getTagName())) {
                return child.getTextContent().trim();
            }
        }
        return null;
    }

    private String readQuietly(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
