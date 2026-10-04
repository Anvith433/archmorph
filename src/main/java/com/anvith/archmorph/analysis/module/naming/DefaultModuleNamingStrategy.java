package com.anvith.archmorph.analysis.module.naming;

import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic naming: the most frequent domain stem in the group, entities
 * counting double. Names are lower-case, valid Java identifiers that do not
 * clash with Java keywords or ArchMorph's reserved shared package names.
 */
@Component
public class DefaultModuleNamingStrategy implements ModuleNamingStrategy {

    public static final Set<String> RESERVED = Set.of(
            "shared", "common", "security", "config", "infrastructure", "application", "exception", "modules");

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
            "record", "var", "yield", "sealed", "permits");

    @Override
    public String determineModuleName(Set<DependencyNode> component) {
        Map<String, Integer> counts = new TreeMap<>();
        for (DependencyNode node : component) {
            String stem = DomainTerms.stem(node.getClassName());
            if (!DomainTerms.isGenericName(node.getClassName())) {
                counts.merge(stem, node.getComponentType() == ComponentType.ENTITY ? 2 : 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(e -> toPackageSegment(e.getKey()))
                .orElse("module");
    }

    @Override
    public String toPackageSegment(String term) {
        String segment = term == null ? "" : term.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (segment.isEmpty() || !Character.isLetter(segment.charAt(0))) {
            segment = "m" + segment;
        }
        if (segment.length() > 40) {
            segment = segment.substring(0, 40);
        }
        if (JAVA_KEYWORDS.contains(segment) || RESERVED.contains(segment)) {
            segment = segment + "module";
        }
        return segment;
    }

    public static boolean isValidModuleName(String name) {
        return name != null && name.matches("^[a-z][a-z0-9]{0,39}$") && !JAVA_KEYWORDS.contains(name);
    }
}
