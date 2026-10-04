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


    /*
     * Additional stereotypes and markers used by the classifier
     */
    public static final String EMBEDDABLE = "Embeddable";

    public static final String MAPPED_SUPERCLASS = "MappedSuperclass";

    public static final String DOCUMENT = "Document";

    public static final String TABLE = "Table";

    public static final String ENABLE_METHOD_SECURITY = "EnableMethodSecurity";

    public static final String ENABLE_GLOBAL_METHOD_SECURITY = "EnableGlobalMethodSecurity";

    public static final String BEAN = "Bean";

    public static final String EXCEPTION_HANDLER = "ExceptionHandler";

    public static final String REQUEST_MAPPING = "RequestMapping";

    public static final String GENERATED = "Generated";

    public static final java.util.Set<String> MAPPING_ANNOTATIONS = java.util.Set.of(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping",
            "DeleteMapping", "PatchMapping");

    public static final java.util.Set<String> ENTITY_RELATIONSHIP_ANNOTATIONS = java.util.Set.of(
            "OneToMany", "ManyToOne", "OneToOne", "ManyToMany",
            "ElementCollection", "Embedded", "EmbeddedId", "DBRef");

    public static final java.util.Set<String> PACKAGE_SCAN_ANNOTATIONS = java.util.Set.of(
            "ComponentScan", "EntityScan", "EnableJpaRepositories",
            "EnableMongoRepositories", "MapperScan", "ConfigurationPropertiesScan",
            "SpringBootApplication", "EnableFeignClients");

}
