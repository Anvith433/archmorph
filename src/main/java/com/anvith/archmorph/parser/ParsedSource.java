package com.anvith.archmorph.parser;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

/**
 * Outcome of parsing one source file. A file that fails to parse is not
 * fatal for the analysis: it is reported and excluded from automatic rewriting.
 *
 * @param compilationUnit the AST, or {@code null} when parsing failed
 * @param source          the raw source text (never logged)
 * @param problems        parser problems with line information when available
 */
public record ParsedSource(CompilationUnit compilationUnit, String source, List<ParseProblem> problems) {

    public boolean successful() {
        return compilationUnit != null && problems.isEmpty();
    }

    /** A single parser problem. */
    public record ParseProblem(int line, String message) {
    }
}
