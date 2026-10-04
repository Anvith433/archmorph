package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.transformation.rewrite.RewriteResult;
import com.anvith.archmorph.analysis.transformation.rewrite.SourceRewriter;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Executes a {@link TransformationPlan}. All decisions were made by the planner; this class only
 * performs them: copy resources, rewrite and write Java files at their planned target paths.
 * The original project is never modified.
 */
@Service
public class DefaultTransformationEngine implements TransformationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultTransformationEngine.class);

    private final SourceRewriter sourceRewriter;
    private final DiffService diffService;

    public DefaultTransformationEngine(SourceRewriter sourceRewriter, DiffService diffService) {
        this.sourceRewriter = sourceRewriter;
        this.diffService = diffService;
    }

    @Override
    public TransformationResult dryRun(ProjectModel model, TransformationPlan plan) {
        return run(model, plan, null);
    }

    @Override
    public TransformationResult execute(ProjectModel model, TransformationPlan plan, Path targetRoot) {
        return run(model, plan, targetRoot.toAbsolutePath().normalize());
    }

    private TransformationResult run(ProjectModel model, TransformationPlan plan, Path destination) {
        long started = System.nanoTime();
        boolean dryRun = destination == null;
        Path projectRoot = model.structure().projectRoot();

        int copied = 0;
        if (!dryRun) {
            WorkspaceManager.resetDirectory(destination);
        }

        Map<String, SourceFile> filesByPath = new HashMap<>();
        model.files().forEach(f -> filesByPath.put(f.getRelativePath(), f));
        Set<String> plannedSources = new HashSet<>();

        List<FileTransformation> results = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int moved = 0;

        for (TransformationPlanEntry entry : plan.getEntries()) {
            String sourceRelative = entry.getSourceFile().toString().replace('\\', '/');
            String targetRelative = entry.getTargetFile().toString().replace('\\', '/');
            plannedSources.add(sourceRelative);
            SourceFile file = filesByPath.get(sourceRelative);
            if (file == null) {
                throw new ArchMorphException(ErrorCode.TRANSFORMATION_ERROR,
                        "The plan refers to a file that is not part of the analysed project.");
            }

            String original = file.getParsed().source();
            String output = original;
            RewriteResult rewrite = null;
            if (file.isParseable()) {
                try {
                    rewrite = sourceRewriter.rewrite(original, entry.getTargetPackage(), plan.getClassMap(),
                            model.registry());
                    output = rewrite.source();
                } catch (RuntimeException e) {
                    throw new ArchMorphException(ErrorCode.TRANSFORMATION_ERROR,
                            "A source file could not be rewritten: " + entry.getSourceFile().getFileName(),
                            "Exclude the class in the module editor or fix its syntax and analyse again.", e);
                }
            }

            if (!dryRun) {
                Path target = contained(destination, targetRelative);
                try {
                    Files.createDirectories(target.getParent());
                    Files.write(target, output.getBytes(charsetOf(projectRoot.resolve(sourceRelative))));
                } catch (IOException e) {
                    throw new ArchMorphException(ErrorCode.TRANSFORMATION_ERROR,
                            "Unable to write a transformed file.", null, e);
                }
            }
            if (!sourceRelative.equals(targetRelative)) {
                moved++;
            }
            int[] lines = diffService.countChangedLines(original, output);
            results.add(new FileTransformation(entry.getId(), sourceRelative, targetRelative, !original.equals(output),
                    rewrite != null && rewrite.packageChanged(),
                    rewrite == null ? List.of() : rewrite.importChanges(),
                    rewrite == null ? 0 : rewrite.qualifiedRewrites(),
                    lines[0], lines[1], rewrite == null ? List.of() : rewrite.warnings()));
            if (rewrite != null) {
                rewrite.warnings().forEach(w -> warnings.add(entry.getSourceFile().getFileName() + ": " + w));
            }
        }

        if (!dryRun) {
            copied = copyRemainingFiles(projectRoot, destination, plannedSources);
        }

        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("{} finished: {} Java files, {} moved, {} other files copied",
                dryRun ? "Dry run" : "Transformation", results.size(), moved, copied);
        return new TransformationResult(dryRun, results.size(), moved, copied, List.copyOf(results),
                List.copyOf(warnings), millis);
    }

    /** Copies everything that is not a planned Java file (pom.xml, resources, wrapper, scripts, ...). */
    private int copyRemainingFiles(Path projectRoot, Path destination, Set<String> plannedSources) {
        int copied = 0;
        try (Stream<Path> stream = Files.walk(projectRoot)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile).sorted()::iterator) {
                if (Files.isSymbolicLink(file)) {
                    continue;
                }
                String relative = projectRoot.relativize(file).toString().replace('\\', '/');
                if (plannedSources.contains(relative)) {
                    continue;
                }
                Path target = contained(destination, relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
                copied++;
            }
        } catch (IOException e) {
            throw new ArchMorphException(ErrorCode.TRANSFORMATION_ERROR, "Unable to copy project resources.", null, e);
        }
        return copied;
    }

    private static Path contained(Path root, String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new ArchMorphException(ErrorCode.TRANSFORMATION_ERROR, "The plan contains an invalid target path.");
        }
        return resolved;
    }

    /** UTF-8 unless the original file is not valid UTF-8 (legacy sources keep ISO-8859-1). */
    private static Charset charsetOf(Path original) {
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(original)));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return StandardCharsets.ISO_8859_1;
        } catch (IOException e) {
            return StandardCharsets.UTF_8;
        }
    }
}
