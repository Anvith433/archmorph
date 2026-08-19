package com.anvith.archmorph.analysis.dependency.util;

import java.util.Set;

public final class DependencyUtils {

    private DependencyUtils() {
    }

    /*
     * Types that should never appear
     * in the architecture graph.
     */
    private static final Set<String> IGNORED_TYPES = Set.of(

            /*
             * Java Types
             */
            "String",
            "Object",

            "Integer",
            "Long",
            "Double",
            "Float",
            "Boolean",
            "Character",
            "Byte",
            "Short",

            /*
             * Collections
             */
            "List",
            "Set",
            "Map",
            "Collection",
            "Optional",
            "Iterable",

            /*
             * Date & Time
             */
            "LocalDate",
            "LocalDateTime",
            "LocalTime",
            "Date",
            "Instant",

            /*
             * Spring MVC
             */
            "ResponseEntity",
            "MultipartFile",
            "Model",
            "ModelMap",
            "BindingResult",
            "WebRequest",

            /*
             * Spring Security
             */
            "Authentication",
            "AuthenticationManager",
            "AuthenticationConfiguration",
            "AuthenticationEntryPoint",
            "PasswordEncoder",
            "GrantedAuthority",
            "UserDetails",
            "UserDetailsService",

            /*
             * Servlet API
             */
            "HttpServletRequest",
            "HttpServletResponse",
            "FilterChain",

            /*
             * Spring Security Filters
             */
            "HttpSecurity",

            /*
             * Validation
             */
            "ConstraintViolationException",
            "MethodArgumentNotValidException",

            /*
             * Exceptions
             */
            "Throwable",
            "Exception",
            "RuntimeException",
            "Error",
            "AccessDeniedException",
            "BadCredentialsException",

            /*
             * Crypto
             */
            "Key"
    );

    /**
     * Remove generic wrappers.
     *
     * Examples:
     *
     * List<User>
     * Optional<User>
     * Set<Role>
     * Map<String, User>
     * Collection<? extends GrantedAuthority>
     */
    public static String normalizeType(String type) {

        if (type == null) {
            return "";
        }

        type = type.trim();

        /*
         * Collection<? extends User>
         */
        if (type.contains("? extends")) {

            return type.substring(
                    type.indexOf("extends") + 8,
                    type.indexOf(">")
            ).trim();
        }

        /*
         * List<User>
         */
        if (type.contains("<") && type.contains(">")) {

            String generic =
                    type.substring(
                            type.indexOf("<") + 1,
                            type.lastIndexOf(">")
                    );

            /*
             * Map<String,User>
             */
            if (generic.contains(",")) {

                generic =
                        generic.substring(
                                generic.lastIndexOf(",") + 1
                        );
            }

            return generic.trim();
        }

        /*
         * Remove package names
         *
         * Example:
         * java.util.List
         */
        if (type.contains(".")) {

            type =
                    type.substring(
                            type.lastIndexOf(".") + 1
                    );
        }

        return type;
    }

    /**
     * Ignore primitive types,
     * generic variables and
     * Java/Spring framework types.
     */
    public static boolean shouldIgnore(String type) {

        if (type == null || type.isBlank()) {
            return true;
        }

        /*
         * Ignore generic type variables
         */
        if (type.matches("^[A-Z]$")) {
            return true;
        }

        /*
         * Ignore arrays
         */
        if (type.endsWith("[]")) {
            return true;
        }

        if (isPrimitive(type)) {
            return true;
        }

        return IGNORED_TYPES.contains(type);
    }

    /**
     * Primitive Types
     */
    private static boolean isPrimitive(String type) {

        return switch (type) {

            case "int",
                    "long",
                    "double",
                    "float",
                    "boolean",
                    "char",
                    "byte",
                    "short" -> true;

            default -> false;
        };
    }

}