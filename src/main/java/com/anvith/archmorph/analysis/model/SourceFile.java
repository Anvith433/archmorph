package com.anvith.archmorph.analysis.model;

import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ParsedSource;
import com.anvith.archmorph.parser.SourceScope;

import java.nio.file.Path;
import java.util.List;

/**
 * One Java source file of the analysed project. The unit of transformation:
 * a file is moved as a whole, together with all types it declares.
 */
public class SourceFile {

    private final String relativePath;
    private final Path absolutePath;
    private final Path sourceRoot;
    private final SourceScope scope;
    private final ParsedSource parsed;
    private final List<ClassMetadata> types;

    public SourceFile(String relativePath, Path absolutePath, Path sourceRoot, SourceScope scope,
                      ParsedSource parsed, List<ClassMetadata> types) {
        this.relativePath = relativePath;
        this.absolutePath = absolutePath;
        this.sourceRoot = sourceRoot;
        this.scope = scope;
        this.parsed = parsed;
        this.types = List.copyOf(types);
    }

    public String getRelativePath() {
        return relativePath;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    public Path getSourceRoot() {
        return sourceRoot;
    }

    public SourceScope getScope() {
        return scope;
    }

    public ParsedSource getParsed() {
        return parsed;
    }

    public boolean isParseable() {
        return parsed.successful();
    }

    /** Every type declared in the file, top-level and nested. */
    public List<ClassMetadata> getTypes() {
        return types;
    }

    public List<ClassMetadata> getTopLevelTypes() {
        return types.stream().filter(ClassMetadata::isTopLevel).toList();
    }

    /** The public top-level type, the one matching the file name, or the first top-level type. */
    public ClassMetadata getPrimaryType() {
        String fileName = absolutePath.getFileName().toString().replace(".java", "");
        List<ClassMetadata> topLevel = getTopLevelTypes();
        return topLevel.stream().filter(t -> t.getClassName().equals(fileName)).findFirst()
                .or(() -> topLevel.stream().filter(ClassMetadata::isPublicType).findFirst())
                .orElse(topLevel.isEmpty() ? null : topLevel.getFirst());
    }

    public String getPackageName() {
        if (parsed.compilationUnit() != null) {
            return parsed.compilationUnit().getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        }
        return null;
    }

    public String getFileName() {
        return absolutePath.getFileName().toString();
    }
}
