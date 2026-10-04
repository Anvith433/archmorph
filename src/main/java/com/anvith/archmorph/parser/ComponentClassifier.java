package com.anvith.archmorph.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Layered, confidence-aware component classifier.
 *
 * <p>Evidence is gathered from every layer, strongest first:</p>
 * <ol>
 *     <li>explicit stereotype annotation (@Service, @Entity, ...)</li>
 *     <li>inheritance / implemented framework interfaces</li>
 *     <li>AST structure (request-mapping methods, @Bean methods, ...)</li>
 *     <li>generic @Component annotation</li>
 *     <li>naming convention</li>
 *     <li>package convention</li>
 * </ol>
 * <p>The highest layer with evidence decides the type. Agreement from other
 * layers raises the confidence; contradicting evidence lowers it. If nothing
 * matches the type is {@link ComponentType#UNKNOWN} with confidence 0.</p>
 */
@Component
public class ComponentClassifier {

    private enum Layer {
        ANNOTATION(0.95),
        INHERITANCE(0.90),
        STRUCTURE(0.78),
        GENERIC_ANNOTATION(0.70),
        NAMING(0.65),
        PACKAGE(0.50);

        final double base;

        Layer(double base) {
            this.base = base;
        }
    }

    private record Candidate(ComponentType type, Layer layer, String evidence) {
    }

    private static final Map<String, ComponentType> ANNOTATION_RULES = orderedMap(
            "SpringBootApplication", ComponentType.APPLICATION,
            "EnableWebSecurity", ComponentType.SECURITY,
            "EnableMethodSecurity", ComponentType.SECURITY,
            "EnableGlobalMethodSecurity", ComponentType.SECURITY,
            "RestControllerAdvice", ComponentType.EXCEPTION_HANDLER,
            "ControllerAdvice", ComponentType.EXCEPTION_HANDLER,
            "RestController", ComponentType.CONTROLLER,
            "Controller", ComponentType.CONTROLLER,
            "DgsComponent", ComponentType.CONTROLLER,
            "Service", ComponentType.SERVICE,
            "Repository", ComponentType.REPOSITORY,
            "Entity", ComponentType.ENTITY,
            "Embeddable", ComponentType.ENTITY,
            "MappedSuperclass", ComponentType.ENTITY,
            "Document", ComponentType.ENTITY,
            "Configuration", ComponentType.CONFIGURATION,
            "ConfigurationProperties", ComponentType.CONFIGURATION);

    /** Spring for GraphQL and Netflix DGS handler methods: the GraphQL equivalent of request mappings. */
    private static final Set<String> GRAPHQL_HANDLER_ANNOTATIONS = Set.of(
            "QueryMapping", "MutationMapping", "SubscriptionMapping", "SchemaMapping", "BatchMapping",
            "DgsQuery", "DgsMutation", "DgsSubscription", "DgsData");

    private static final Map<String, ComponentType> SUPERTYPE_RULES = orderedMap(
            "OncePerRequestFilter", ComponentType.FILTER,
            "GenericFilterBean", ComponentType.FILTER,
            "Filter", ComponentType.FILTER,
            "HandlerInterceptor", ComponentType.FILTER,
            "JpaRepository", ComponentType.REPOSITORY,
            "CrudRepository", ComponentType.REPOSITORY,
            "PagingAndSortingRepository", ComponentType.REPOSITORY,
            "ListCrudRepository", ComponentType.REPOSITORY,
            "MongoRepository", ComponentType.REPOSITORY,
            "ReactiveCrudRepository", ComponentType.REPOSITORY,
            "JpaSpecificationExecutor", ComponentType.REPOSITORY,
            "AuthenticationEntryPoint", ComponentType.SECURITY,
            "AccessDeniedHandler", ComponentType.SECURITY,
            "UserDetailsService", ComponentType.SECURITY,
            "AuthenticationProvider", ComponentType.SECURITY,
            "AuthenticationSuccessHandler", ComponentType.SECURITY,
            "AuthenticationFailureHandler", ComponentType.SECURITY,
            "LogoutHandler", ComponentType.SECURITY,
            "DataFetcherExceptionHandler", ComponentType.EXCEPTION_HANDLER,
            "DataFetcherExceptionResolver", ComponentType.EXCEPTION_HANDLER,
            "DataFetcherExceptionResolverAdapter", ComponentType.EXCEPTION_HANDLER,
            "WebMvcConfigurer", ComponentType.CONFIGURATION,
            "WebSecurityConfigurerAdapter", ComponentType.SECURITY,
            "RuntimeException", ComponentType.EXCEPTION,
            "Exception", ComponentType.EXCEPTION,
            "ResponseStatusException", ComponentType.EXCEPTION,
            "SpringBootServletInitializer", ComponentType.APPLICATION);

    private static final List<Map.Entry<String, ComponentType>> SUFFIX_RULES = List.of(
            Map.entry("RestController", ComponentType.CONTROLLER),
            Map.entry("Controller", ComponentType.CONTROLLER),
            Map.entry("Resource", ComponentType.CONTROLLER),
            Map.entry("Endpoint", ComponentType.CONTROLLER),
            Map.entry("DataFetcher", ComponentType.CONTROLLER),
            Map.entry("Datafetcher", ComponentType.CONTROLLER),
            Map.entry("ServiceImpl", ComponentType.SERVICE),
            Map.entry("Service", ComponentType.SERVICE),
            Map.entry("Repository", ComponentType.REPOSITORY),
            Map.entry("Repo", ComponentType.REPOSITORY),
            Map.entry("Dao", ComponentType.REPOSITORY),
            Map.entry("DAO", ComponentType.REPOSITORY),
            Map.entry("Entity", ComponentType.ENTITY),
            Map.entry("Dto", ComponentType.DTO),
            Map.entry("DTO", ComponentType.DTO),
            Map.entry("ViewModel", ComponentType.DTO),
            Map.entry("VM", ComponentType.DTO),
            Map.entry("Request", ComponentType.DTO),
            Map.entry("Response", ComponentType.DTO),
            Map.entry("Payload", ComponentType.DTO),
            Map.entry("Form", ComponentType.DTO),
            Map.entry("ExceptionHandler", ComponentType.EXCEPTION_HANDLER),
            Map.entry("Exception", ComponentType.EXCEPTION),
            Map.entry("Configuration", ComponentType.CONFIGURATION),
            Map.entry("Config", ComponentType.CONFIGURATION),
            Map.entry("Properties", ComponentType.CONFIGURATION),
            Map.entry("Filter", ComponentType.FILTER),
            Map.entry("Interceptor", ComponentType.FILTER),
            Map.entry("Application", ComponentType.APPLICATION));

    private static final Map<String, ComponentType> PACKAGE_RULES = orderedMap(
            "controller", ComponentType.CONTROLLER,
            "controllers", ComponentType.CONTROLLER,
            "web", ComponentType.CONTROLLER,
            "rest", ComponentType.CONTROLLER,
            "api", ComponentType.CONTROLLER,
            "service", ComponentType.SERVICE,
            "services", ComponentType.SERVICE,
            "repository", ComponentType.REPOSITORY,
            "repositories", ComponentType.REPOSITORY,
            "repo", ComponentType.REPOSITORY,
            "dao", ComponentType.REPOSITORY,
            "persistence", ComponentType.REPOSITORY,
            "entity", ComponentType.ENTITY,
            "entities", ComponentType.ENTITY,
            "model", ComponentType.ENTITY,
            "models", ComponentType.ENTITY,
            "domain", ComponentType.ENTITY,
            "dto", ComponentType.DTO,
            "vm", ComponentType.DTO,
            "dtos", ComponentType.DTO,
            "payload", ComponentType.DTO,
            "request", ComponentType.DTO,
            "response", ComponentType.DTO,
            "exception", ComponentType.EXCEPTION,
            "exceptions", ComponentType.EXCEPTION,
            "config", ComponentType.CONFIGURATION,
            "configuration", ComponentType.CONFIGURATION,
            "security", ComponentType.SECURITY,
            "auth", ComponentType.SECURITY,
            "jwt", ComponentType.SECURITY,
            "filter", ComponentType.FILTER,
            "filters", ComponentType.FILTER);

    private static final Set<String> SECURITY_NAME_TOKENS = Set.of("Security", "Jwt", "JWT", "Auth", "Token");

    public ComponentClassification classify(ClassMetadata metadata) {
        List<Candidate> candidates = new ArrayList<>();

        annotationLayer(metadata, candidates);
        inheritanceLayer(metadata, candidates);
        structureLayer(metadata, candidates);
        if (metadata.getAnnotations().contains(AnnotationConstants.COMPONENT)) {
            candidates.add(new Candidate(componentRefinement(metadata), Layer.GENERIC_ANNOTATION, "@Component"));
        }
        namingLayer(metadata, candidates);
        packageLayer(metadata, candidates);

        if (candidates.isEmpty()) {
            return ComponentClassification.unknown("no annotation, inheritance, naming or package signal");
        }

        Candidate winner = candidates.getFirst();
        double confidence = winner.layer().base;
        List<String> agreeing = new ArrayList<>();
        List<String> contradicting = new ArrayList<>();
        agreeing.add(winner.evidence());

        for (Candidate candidate : candidates.subList(1, candidates.size())) {
            if (candidate.type() == winner.type()) {
                if (candidate.layer() != winner.layer()) {
                    confidence += 0.04;
                }
                agreeing.add(candidate.evidence());
            } else {
                if (candidate.layer().ordinal() <= Layer.NAMING.ordinal()
                        && candidate.layer() != winner.layer()) {
                    confidence -= 0.08;
                }
                contradicting.add(candidate.evidence() + " suggests " + candidate.type());
            }
        }

        confidence = Math.max(0.30, Math.min(0.99, confidence));
        List<String> evidence = new ArrayList<>(agreeing);
        evidence.addAll(contradicting);
        return new ComponentClassification(winner.type(), round(confidence), List.copyOf(evidence));
    }

    private void annotationLayer(ClassMetadata metadata, List<Candidate> candidates) {
        for (Map.Entry<String, ComponentType> rule : ANNOTATION_RULES.entrySet()) {
            if (metadata.getAnnotations().contains(rule.getKey())) {
                candidates.add(new Candidate(rule.getValue(), Layer.ANNOTATION, "@" + rule.getKey()));
            }
        }
    }

    private void inheritanceLayer(ClassMetadata metadata, List<Candidate> candidates) {
        List<String> supertypes = new ArrayList<>();
        if (metadata.getSuperClass() != null) {
            supertypes.add(metadata.getSuperClass());
        }
        supertypes.addAll(metadata.getInterfaces());

        for (Map.Entry<String, ComponentType> rule : SUPERTYPE_RULES.entrySet()) {
            for (String supertype : supertypes) {
                if (simpleName(supertype).equals(rule.getKey())) {
                    String verb = metadata.isInterface() || !rule.getKey().equals(simpleName(metadata.getSuperClass()))
                            ? "implements/extends " : "extends ";
                    candidates.add(new Candidate(rule.getValue(), Layer.INHERITANCE, verb + rule.getKey()));
                }
            }
        }
        // Domain exceptions often extend another project exception, e.g. UserNotFound extends NotFoundException.
        if (metadata.getSuperClass() != null && simpleName(metadata.getSuperClass()).endsWith("Exception")
                && !SUPERTYPE_RULES.containsKey(simpleName(metadata.getSuperClass()))) {
            candidates.add(new Candidate(ComponentType.EXCEPTION, Layer.INHERITANCE,
                    "extends " + simpleName(metadata.getSuperClass())));
        }
    }

    private void structureLayer(ClassMetadata metadata, List<Candidate> candidates) {
        Set<String> methodAnnotations = metadata.getMethodAnnotations();
        if (methodAnnotations.stream().anyMatch(AnnotationConstants.MAPPING_ANNOTATIONS::contains)
                && !metadata.isInterface()) {
            candidates.add(new Candidate(ComponentType.CONTROLLER, Layer.STRUCTURE, "declares request-mapping methods"));
        }
        if (methodAnnotations.stream().anyMatch(GRAPHQL_HANDLER_ANNOTATIONS::contains) && !metadata.isInterface()) {
            candidates.add(new Candidate(ComponentType.CONTROLLER, Layer.STRUCTURE, "declares GraphQL handler methods"));
        }
        if (methodAnnotations.contains(AnnotationConstants.EXCEPTION_HANDLER)) {
            candidates.add(new Candidate(ComponentType.EXCEPTION_HANDLER, Layer.STRUCTURE, "declares @ExceptionHandler methods"));
        }
        if (methodAnnotations.contains(AnnotationConstants.BEAN)) {
            candidates.add(new Candidate(ComponentType.CONFIGURATION, Layer.STRUCTURE, "declares @Bean methods"));
        }
        boolean hasMain = metadata.getMethodDetails().stream().anyMatch(m -> m.name().equals("main")
                && m.modifiers().contains("static") && m.parameters().size() == 1
                && m.parameters().getFirst().type().replace(" ", "").matches("String(\\[\\]|\\.\\.\\.)"));
        if (hasMain && metadata.getKind() == TypeKind.CLASS) {
            candidates.add(new Candidate(ComponentType.APPLICATION, Layer.STRUCTURE, "declares a static main(String[]) method"));
        }
        if (metadata.getKind() == TypeKind.RECORD && metadata.getMethodDetails().isEmpty()
                && metadata.getAnnotations().isEmpty()) {
            candidates.add(new Candidate(ComponentType.DTO, Layer.STRUCTURE, "data-only record"));
        }
    }

    private ComponentType componentRefinement(ClassMetadata metadata) {
        String name = metadata.getClassName();
        if (name != null && SECURITY_NAME_TOKENS.stream().anyMatch(name::contains)) {
            return ComponentType.SECURITY;
        }
        return ComponentType.COMPONENT;
    }

    private void namingLayer(ClassMetadata metadata, List<Candidate> candidates) {
        String name = metadata.getClassName();
        if (name == null) {
            return;
        }
        for (Map.Entry<String, ComponentType> rule : SUFFIX_RULES) {
            if (name.endsWith(rule.getKey()) && name.length() > rule.getKey().length()) {
                ComponentType type = rule.getValue();
                if (metadata.isInterface() && type != ComponentType.REPOSITORY && type != ComponentType.SERVICE) {
                    continue;
                }
                candidates.add(new Candidate(type, Layer.NAMING, "class name ends with " + rule.getKey()));
                return;
            }
        }
    }

    private void packageLayer(ClassMetadata metadata, List<Candidate> candidates) {
        String packageName = metadata.getPackageName();
        if (packageName == null || packageName.isBlank()) {
            return;
        }
        String[] segments = packageName.toLowerCase(Locale.ROOT).split("\\.");
        for (int i = segments.length - 1; i >= 0; i--) {
            ComponentType type = PACKAGE_RULES.get(segments[i]);
            if (type != null) {
                candidates.add(new Candidate(type, Layer.PACKAGE, "package segment '" + segments[i] + "'"));
                return;
            }
        }
    }

    private static String simpleName(String type) {
        if (type == null) {
            return "";
        }
        String raw = type.contains("<") ? type.substring(0, type.indexOf('<')) : type;
        return raw.substring(raw.lastIndexOf('.') + 1).trim();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Map<String, ComponentType> orderedMap(Object... pairs) {
        java.util.LinkedHashMap<String, ComponentType> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (ComponentType) pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
