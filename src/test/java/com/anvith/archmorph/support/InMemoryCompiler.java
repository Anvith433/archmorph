package com.anvith.archmorph.support;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Compiles a project's main and test sources with the JDK compiler against the test classpath. */
public final class InMemoryCompiler {

    private InMemoryCompiler() {
    }

    /** @return compiler error messages; empty when compilation succeeded */
    public static List<String> compile(Path projectRoot, Path output) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<Path> sources;
        try (Stream<Path> stream = Files.walk(projectRoot)) {
            sources = stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/") || p.toString().contains("/src/test/java/"))
                    .toList();
        }
        Files.createDirectories(output);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of("-d", output.toString(), "-classpath", System.getProperty("java.class.path"),
                    "-proc:none", "-nowarn", "-encoding", "UTF-8");
            compiler.getTask(null, files, diagnostics, options, null, files.getJavaFileObjectsFromPaths(sources)).call();
        }
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> (d.getSource() == null ? "" : projectRoot.relativize(Path.of(d.getSource().toUri())) + ":"
                        + d.getLineNumber() + " ") + d.getMessage(null))
                .toList();
    }
}
