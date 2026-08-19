package com.anvith.archmorph.analysis.module;

public interface ModuleClassifier {

    /**
     * Returns the module name
     * for the given class.
     */
    String classify(String className);

}