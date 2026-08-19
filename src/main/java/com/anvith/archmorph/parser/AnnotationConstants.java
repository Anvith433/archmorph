package com.anvith.archmorph.parser;

public final class AnnotationConstants {

    private AnnotationConstants() {
    }

    /*
     * Spring MVC
     */
    public static final String REST_CONTROLLER = "RestController";

    public static final String CONTROLLER = "Controller";

    /*
     * Service Layer
     */
    public static final String SERVICE = "Service";

    /*
     * Persistence Layer
     */
    public static final String REPOSITORY = "Repository";

    /*
     * Domain
     */
    public static final String ENTITY = "Entity";

    /*
     * Configuration
     */
    public static final String CONFIGURATION = "Configuration";

    public static final String COMPONENT = "Component";

    public static final String SPRING_BOOT_APPLICATION =
            "SpringBootApplication";

    /*
     * Exception Handling
     */
    public static final String REST_CONTROLLER_ADVICE =
            "RestControllerAdvice";

    public static final String CONTROLLER_ADVICE =
            "ControllerAdvice";

    /*
     * Security
     */
    public static final String ENABLE_WEB_SECURITY =
            "EnableWebSecurity";

    /*
     * Configuration Properties
     */
    public static final String CONFIGURATION_PROPERTIES =
            "ConfigurationProperties";


    /*
 * Lombok
 */
public static final String REQUIRED_ARGS_CONSTRUCTOR =
        "RequiredArgsConstructor";

public static final String ALL_ARGS_CONSTRUCTOR =
        "AllArgsConstructor";

public static final String NO_ARGS_CONSTRUCTOR =
        "NoArgsConstructor";

}