package com.anvith.archmorph.analysis.dependency.extractor;

/**
 * Common contract for all dependency extractors.
 *
 * <p>Every extractor finds one kind of dependency (fields, constructors,
 * method signatures, calls, object creation, inheritance, annotations,
 * other type references) in one top-level type and adds edges through
 * {@link ExtractionContext}.</p>
 */
public interface DependencyExtractor {

    void extract(ExtractionContext context);
}
