package com.anvith.archmorph.parser;

public enum ComponentType {

    /*
     * Spring MVC
     */
    CONTROLLER,

    /*
     * Business Layer
     */
    SERVICE,

    /*
     * Persistence Layer
     */
    REPOSITORY,

    /*
     * Domain Layer
     */
    ENTITY,

    DTO,

    /*
     * Configuration
     */
    CONFIGURATION,

    APPLICATION,

    COMPONENT,

    /*
     * Exception Handling
     */
    EXCEPTION,

    EXCEPTION_HANDLER,

    /*
     * Security
     */
    FILTER,

    SECURITY,

    /*
     * Unknown
     */
    UNKNOWN

}