package com.anvith.archmorph.analysis.dependency;

/**
 * Represents the reason why two classes
 * are connected in the dependency graph.
 */
public enum DependencyType {

    /*
     * Constructor Injection
     */
    CONSTRUCTOR,

    /*
     * Field Injection
     */
    FIELD,

    /*
     * Method Parameter
     */
    METHOD_PARAMETER,

    /*
     * Method Return Type
     */
    METHOD_RETURN,

    /*
     * extends
     */
    INHERITANCE,

    /*
     * implements
     */
    IMPLEMENTATION,

    /*
     * Generic Types
     * Example:
     * List<User>
     */
    GENERIC,

    /*
     * Method Call
     */
    METHOD_CALL

}