package com.anvith.archmorph.analysis.module.naming;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts domain vocabulary from class names, paths and method names.
 *
 * <pre>
 * UserServiceImpl         → stem "user"        tokens [user]
 * CreateOrderItemRequest  → stem "orderitem"   tokens [order, item]
 * /api/v1/payments        → term "payment"
 * </pre>
 */
public final class DomainTerms {

    private DomainTerms() {
    }

    /** Role suffixes, longest first so "ServiceImpl" wins over "Impl". */
    private static final List<String> ROLE_SUFFIXES = List.of(
            "RestController", "Controller", "Resource", "Endpoint", "ServiceImpl", "Service", "Impl",
            "Repository", "Repo", "Dao", "DAO", "Entity", "Dto", "DTO", "Request", "Response", "Payload",
            "Form", "Command", "Query", "View", "Model", "Mapper", "Converter", "Assembler", "Exception",
            "Validator", "Facade", "Manager", "Handler", "Listener", "Event", "Specification", "Spec",
            "Projection", "Summary", "Details", "Detail", "Info", "Data", "Client", "Gateway", "Adapter",
            "Factory", "Builder", "Helper", "Utils", "Util", "Config", "Configuration", "Properties",
            "Status", "Type", "Test", "Tests", "IT");

    private static final List<String> VERB_PREFIXES = List.of(
            "Create", "Update", "Delete", "Get", "List", "Find", "Search", "Add", "Remove", "Patch", "Save",
            "New", "Edit", "Register", "Upsert", "Default", "Abstract", "Base", "Simple", "Paged", "Page");

    /** Terms that never name a business module. */
    public static final Set<String> GENERIC_TERMS = Set.of(
            "", "base", "abstract", "common", "shared", "app", "application", "api", "main", "web", "global",
            "default", "generic", "core", "util", "utils", "helper", "error", "errors", "exception", "response",
            "request", "page", "result", "message", "constants", "constant", "config", "custom", "my", "impl",
            "v1", "v2", "v3", "rest", "public", "internal", "entity", "dto", "model", "data", "info",
            "resource", "not", "found", "bad", "invalid", "already", "exists", "exist", "unauthorized",
            "forbidden", "conflict", "validation");

    /** Domain stem of a class name: role suffixes and verb prefixes removed, lower case. */
    public static String stem(String className) {
        return String.join("", tokens(className));
    }

    /** Lower-case domain tokens of a class name. */
    public static List<String> tokens(String className) {
        if (className == null || className.isBlank()) {
            return List.of();
        }
        String name = className;
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String suffix : ROLE_SUFFIXES) {
                if (name.endsWith(suffix) && name.length() > suffix.length()) {
                    name = name.substring(0, name.length() - suffix.length());
                    stripped = true;
                    break;
                }
            }
        }
        for (String prefix : VERB_PREFIXES) {
            if (name.startsWith(prefix) && name.length() > prefix.length()
                    && Character.isUpperCase(name.charAt(prefix.length()))) {
                name = name.substring(prefix.length());
                break;
            }
        }
        List<String> tokens = new ArrayList<>();
        for (String part : splitCamelCase(name)) {
            String token = singular(part.toLowerCase(Locale.ROOT));
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        // "UserNotFound" → [user]: drop generic words when a domain word remains.
        List<String> domain = tokens.stream().filter(t -> !GENERIC_TERMS.contains(t)).toList();
        return domain.isEmpty() ? tokens : domain;
    }

    public static List<String> splitCamelCase(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null) {
            return parts;
        }
        for (String part : text.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[_\\-\\s$]+")) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return parts;
    }

    /** Naive English singularisation, adequate for identifiers. */
    public static String singular(String word) {
        if (word.length() <= 3) {
            return word;
        }
        if (word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.endsWith("sses") || word.endsWith("xes") || word.endsWith("ches") || word.endsWith("shes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    /** Domain terms of a request path: "/api/v1/order-items/{id}" → [orderitem]. */
    public static List<String> pathTerms(String path) {
        List<String> terms = new ArrayList<>();
        if (path == null) {
            return terms;
        }
        for (String segment : path.split("/")) {
            if (segment.isBlank() || segment.startsWith("{") || segment.startsWith("$")) {
                continue;
            }
            String term = String.join("", splitCamelCase(segment).stream()
                    .map(s -> singular(s.toLowerCase(Locale.ROOT))).toList());
            if (!GENERIC_TERMS.contains(term) && term.matches("[a-z][a-z0-9]*")) {
                terms.add(term);
            }
        }
        return terms;
    }

    /** Lower-case tokens of method names (findOrdersByUser → [find, order, by, user]). */
    public static List<String> methodTokens(String methodName) {
        return splitCamelCase(methodName).stream().map(s -> singular(s.toLowerCase(Locale.ROOT))).toList();
    }

    public static boolean isGeneric(String term) {
        return term == null || GENERIC_TERMS.contains(term) || term.length() < 2;
    }

    /** Token-level Jaccard similarity. */
    public static double tokenSimilarity(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (String.join("", a).equals(String.join("", b))) {
            return 1.0;
        }
        Set<String> union = new java.util.HashSet<>(a);
        union.addAll(b);
        long common = a.stream().distinct().filter(b::contains).count();
        double jaccard = (double) common / union.size();
        boolean prefix = startsWith(a, b) || startsWith(b, a);
        return prefix ? Math.max(jaccard, 0.6) : jaccard;
    }

    private static boolean startsWith(List<String> list, List<String> prefix) {
        return prefix.size() < list.size() && list.subList(0, prefix.size()).equals(prefix);
    }
}
