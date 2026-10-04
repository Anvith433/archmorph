package com.anvith.archmorph.analysis.model;

import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ProjectStructure;
import com.anvith.archmorph.parser.SourceScope;

import java.util.List;
import java.util.Optional;

/** Parsed view of a project: structure, files, registry. Immutable after construction. */
public record ProjectModel(ProjectStructure structure, List<SourceFile> files, ProjectClassRegistry registry) {

    public List<SourceFile> mainFiles() {
        return files.stream().filter(f -> f.getScope() == SourceScope.MAIN).toList();
    }

    public List<SourceFile> testFiles() {
        return files.stream().filter(f -> f.getScope() == SourceScope.TEST).toList();
    }

    public List<SourceFile> unparseableFiles() {
        return files.stream().filter(f -> !f.isParseable()).toList();
    }

    public List<ClassMetadata> allTypes() {
        return files.stream().flatMap(f -> f.getTypes().stream()).toList();
    }

    public List<ClassMetadata> mainTopLevelTypes() {
        return mainFiles().stream().flatMap(f -> f.getTopLevelTypes().stream()).toList();
    }

    public Optional<SourceFile> fileOf(String qualifiedName) {
        return files.stream()
                .filter(f -> f.getTypes().stream().anyMatch(t -> t.getQualifiedName().equals(qualifiedName)))
                .findFirst();
    }
}
