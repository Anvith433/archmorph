package com.anvith.archmorph.analysis.dependency;

/**
 * Where a dependency occurs.
 *
 * @param file project-relative path using '/' separators
 * @param line 1-based line, 0 when unknown
 */
public record SourceLocation(String file, int line) {

    @Override
    public String toString() {
        return line > 0 ? file + ":" + line : file;
    }
}
