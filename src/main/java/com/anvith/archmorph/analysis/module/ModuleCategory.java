package com.anvith.archmorph.analysis.module;

/**
 * Where a class belongs in the target architecture.
 *
 * <ul>
 *     <li>{@code BUSINESS_MODULE} – owned by one business module</li>
 *     <li>{@code SHARED} – used across modules; kept centralised, never duplicated</li>
 *     <li>{@code INFRASTRUCTURE} – technical adapters (clients, messaging, storage)</li>
 *     <li>{@code CONFIGURATION} – framework/bean configuration</li>
 *     <li>{@code SECURITY} – authentication, authorisation, filters</li>
 *     <li>{@code APPLICATION} – the application entry point</li>
 *     <li>{@code UNKNOWN} – could not be placed with any confidence</li>
 * </ul>
 */
public enum ModuleCategory {
    BUSINESS_MODULE,
    SHARED,
    INFRASTRUCTURE,
    CONFIGURATION,
    SECURITY,
    APPLICATION,
    UNKNOWN
}
