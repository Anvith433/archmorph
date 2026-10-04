package com.anvith.archmorph.analysis.validation;

/** The seven validation levels, executed in order. */
public enum ValidationLevel {
    FILESYSTEM("Filesystem"),
    JAVA_PARSING("Java Parsing"),
    PACKAGE_CONSISTENCY("Package Consistency"),
    IMPORT_RESOLUTION("Import Resolution"),
    DEPENDENCY_GRAPH("Dependency Graph"),
    ARCHITECTURE_RULES("Architecture Rules"),
    BUILD("Build");

    private final String label;

    ValidationLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
