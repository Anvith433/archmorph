package com.anvith.archmorph.analysis.dependency.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Helpers for type names. The ignored-type list is used only when a name
 * cannot be resolved through imports or packages: it prevents common
 * framework/JDK names from being matched to a project class by simple name.
 * Additional names can be configured via {@code archmorph.analysis.ignored-types}.
 */
public final class DependencyUtils {

    private DependencyUtils() {
    }

    /** Types that should never appear in the architecture graph by simple-name fallback. */
    public static final Set<String> IGNORED_TYPES = Set.of(
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte", "Short",
            "Number", "Void", "Class", "Enum", "Record", "Iterable", "Comparable", "Runnable", "Thread",
            "StringBuilder", "Math", "System", "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface",
            "List", "Set", "Map", "Collection", "Optional", "Stream", "Collectors", "Arrays", "Collections",
            "LocalDate", "LocalDateTime", "LocalTime", "Date", "Instant", "Duration", "UUID", "BigDecimal", "BigInteger",
            "ResponseEntity", "MultipartFile", "Model", "ModelMap", "BindingResult", "WebRequest", "HttpStatus",
            "Authentication", "AuthenticationManager", "AuthenticationConfiguration", "AuthenticationEntryPoint",
            "PasswordEncoder", "GrantedAuthority", "UserDetails", "UserDetailsService",
            "HttpServletRequest", "HttpServletResponse", "FilterChain", "HttpSecurity",
            "ConstraintViolationException", "MethodArgumentNotValidException",
            "Throwable", "Exception", "RuntimeException", "Error", "IllegalArgumentException",
            "IllegalStateException", "AccessDeniedException", "BadCredentialsException",
            "Key", "Page", "Pageable", "Sort", "Logger", "LoggerFactory");

    /**
     * Extract the referenced type names from a written type.
     *
     * <pre>
     * List&lt;User&gt;                       → [List, User]
     * Map&lt;String, Order&gt;               → [Map, String, Order]
     * Collection&lt;? extends Role&gt;       → [Collection, Role]
     * java.util.List&lt;com.x.User&gt;      → [java.util.List, com.x.User]
     * </pre>
     */
    public static List<String> referencedTypeNames(String type) {
        List<String> names = new ArrayList<>();
        if (type == null) {
            return names;
        }
        for (String token : type.split("[<>,\\[\\]\\s&?]+")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty() || trimmed.equals("extends") || trimmed.equals("super")) {
                continue;
            }
            names.add(trimmed);
        }
        return names;
    }

    /**
     * Legacy helper: the most specific project-relevant type in a written type.
     * {@code Map<String, User>} → {@code User}; {@code List<User>} → {@code User}.
     */
    public static String normalizeType(String type) {
        List<String> names = referencedTypeNames(type);
        for (int i = names.size() - 1; i >= 0; i--) {
            String simple = names.get(i).substring(names.get(i).lastIndexOf('.') + 1);
            if (!shouldIgnore(simple)) {
                return simple;
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        String first = names.getFirst();
        return first.substring(first.lastIndexOf('.') + 1);
    }

    /** Ignore primitive types, single-letter type variables and common framework/JDK types. */
    public static boolean shouldIgnore(String type) {
        if (type == null || type.isBlank()) {
            return true;
        }
        if (type.matches("^[A-Z]$")) {
            return true;
        }
        if (type.endsWith("[]")) {
            return true;
        }
        if (isPrimitive(type)) {
            return true;
        }
        return IGNORED_TYPES.contains(type);
    }

    private static boolean isPrimitive(String type) {
        return switch (type) {
            case "int", "long", "double", "float", "boolean", "char", "byte", "short", "void", "var" -> true;
            default -> false;
        };
    }
}
