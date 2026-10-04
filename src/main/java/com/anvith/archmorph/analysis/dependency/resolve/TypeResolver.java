package com.anvith.archmorph.analysis.dependency.resolve;

import com.anvith.archmorph.analysis.dependency.util.DependencyUtils;
import com.anvith.archmorph.analysis.registry.ProjectClassInfo;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic, import-aware type-name resolution following the Java
 * scoping rules that matter for architecture analysis:
 *
 * <ol>
 *     <li>fully-qualified names ({@code com.demo.entity.User})</li>
 *     <li>types declared in the same file, including nested types</li>
 *     <li>single-type imports</li>
 *     <li>same-package types</li>
 *     <li>on-demand (wildcard) imports</li>
 *     <li>unique simple-name fallback (low confidence, flagged)</li>
 * </ol>
 *
 * <p>This distinguishes {@code com.demo.user.User} from
 * {@code com.demo.admin.User} without relying on simple-name matching.</p>
 */
public class TypeResolver {

    private final ProjectClassRegistry registry;
    private final Set<String> ignoredSimpleNames;

    public TypeResolver(ProjectClassRegistry registry, Set<String> additionalIgnoredTypes) {
        this.registry = registry;
        this.ignoredSimpleNames = additionalIgnoredTypes;
    }

    public ProjectClassRegistry registry() {
        return registry;
    }

    /**
     * Resolve a (possibly scoped) type name such as {@code User},
     * {@code Outer.Inner} or {@code com.demo.User}.
     */
    public ResolvedType resolve(String name, TypeContext context) {
        if (name == null || name.isBlank()) {
            return ResolvedType.unresolved();
        }
        String[] parts = name.split("\\.");

        // 1. Fully qualified (or package-qualified nested) name.
        if (parts.length > 1 && Character.isLowerCase(parts[0].charAt(0))) {
            Optional<ProjectClassInfo> exact = registry.findByQualifiedName(name);
            if (exact.isPresent()) {
                return internal(exact.get(), 1.0, ResolvedType.Resolution.FULLY_QUALIFIED);
            }
            return ResolvedType.external(name);
        }

        ResolvedType head = resolveSimple(parts[0], context);
        if (parts.length == 1 || head.qualifiedName() == null) {
            return head;
        }
        String candidate = head.qualifiedName() + name.substring(parts[0].length());
        if (head.internal()) {
            Optional<ProjectClassInfo> nested = registry.findByQualifiedName(candidate);
            if (nested.isPresent()) {
                return internal(nested.get(), head.confidence(), head.resolution());
            }
            // Member type we do not know (e.g. inherited); attribute it to the owner.
            return new ResolvedType(head.qualifiedName(), head.topLevelQualifiedName(), true,
                    head.confidence() * 0.8, head.resolution());
        }
        return ResolvedType.external(candidate);
    }

    private ResolvedType resolveSimple(String simple, TypeContext context) {
        String declared = context.declaredTypes().get(simple);
        if (declared != null) {
            Optional<ProjectClassInfo> info = registry.findByQualifiedName(declared);
            if (info.isPresent()) {
                return internal(info.get(), 1.0, ResolvedType.Resolution.DECLARED_IN_FILE);
            }
        }

        String imported = context.singleImports().get(simple);
        if (imported != null) {
            return registry.findByQualifiedName(imported)
                    .map(info -> internal(info, 1.0, ResolvedType.Resolution.SINGLE_IMPORT))
                    .orElse(ResolvedType.external(imported));
        }

        String samePackage = context.packageName().isEmpty() ? simple : context.packageName() + "." + simple;
        Optional<ProjectClassInfo> inPackage = registry.findByQualifiedName(samePackage);
        if (inPackage.isPresent()) {
            return internal(inPackage.get(), 0.97, ResolvedType.Resolution.SAME_PACKAGE);
        }

        for (String wildcard : context.wildcardImports()) {
            Optional<ProjectClassInfo> viaWildcard = registry.findByQualifiedName(wildcard + "." + simple);
            if (viaWildcard.isPresent()) {
                return internal(viaWildcard.get(), 0.92, ResolvedType.Resolution.WILDCARD_IMPORT);
            }
        }

        if (DependencyUtils.shouldIgnore(simple) || ignoredSimpleNames.contains(simple)) {
            return ResolvedType.external("java.lang." + simple);
        }

        List<ProjectClassInfo> candidates = registry.findBySimpleName(simple).stream()
                .filter(info -> !info.isNested())
                .toList();
        if (candidates.size() == 1) {
            boolean externalWildcards = context.wildcardImports().stream()
                    .anyMatch(w -> registry.classesInPackage(w).isEmpty());
            return internal(candidates.getFirst(), externalWildcards ? 0.35 : 0.5,
                    ResolvedType.Resolution.SIMPLE_NAME_FALLBACK);
        }
        return ResolvedType.unresolved();
    }

    /** Resolve an internal type by qualified name (used for symbol-solver results). */
    public ResolvedType resolveQualified(String qualifiedName, double confidence, ResolvedType.Resolution how) {
        return registry.findByQualifiedName(qualifiedName)
                .map(info -> internal(info, confidence, how))
                .orElse(ResolvedType.external(qualifiedName));
    }

    private static ResolvedType internal(ProjectClassInfo info, double confidence, ResolvedType.Resolution how) {
        return new ResolvedType(info.getQualifiedName(), info.getTopLevelQualifiedName(), true, confidence, how);
    }
}
