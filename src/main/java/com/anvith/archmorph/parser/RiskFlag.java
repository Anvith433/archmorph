package com.anvith.archmorph.parser;

/**
 * Source characteristics that make automatic transformation less safe.
 * The planner turns these into safety levels; they are never ignored.
 */
public enum RiskFlag {
    /** Class.forName, getDeclaredMethod, ClassLoader.loadClass, ... */
    REFLECTION,
    /** Proxy.newProxyInstance, bytecode generation libraries. */
    DYNAMIC_CLASS_GENERATION,
    /** String literal that looks like a project package or class name. */
    STRING_CLASS_REFERENCE,
    /** @ComponentScan / @EntityScan / @EnableJpaRepositories with explicit packages. */
    PACKAGE_SCANNING_CONFIGURATION,
    /** @Generated annotation or "generated" header comment. */
    GENERATED_CODE,
    /** Type uses package-private members that may become inaccessible after a move. */
    PACKAGE_PRIVATE_ACCESS
}
