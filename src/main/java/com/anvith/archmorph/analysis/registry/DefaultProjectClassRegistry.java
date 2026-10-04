package com.anvith.archmorph.analysis.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** In-memory registry, created once per analysis. */
public class DefaultProjectClassRegistry implements ProjectClassRegistry {

    private final Map<String, ProjectClassInfo> byQualifiedName = new LinkedHashMap<>();

    private final Map<String, List<ProjectClassInfo>> bySimpleName = new LinkedHashMap<>();

    private final Map<String, List<ProjectClassInfo>> byPackage = new TreeMap<>();

    @Override
    public void register(ProjectClassInfo classInfo) {
        if (classInfo == null || classInfo.getQualifiedName() == null) {
            return;
        }
        ProjectClassInfo previous = byQualifiedName.putIfAbsent(classInfo.getQualifiedName(), classInfo);
        if (previous != null) {
            return;
        }
        bySimpleName.computeIfAbsent(classInfo.getClassName(), k -> new ArrayList<>()).add(classInfo);
        if (!classInfo.isNested()) {
            byPackage.computeIfAbsent(classInfo.getPackageName() == null ? "" : classInfo.getPackageName(),
                    k -> new ArrayList<>()).add(classInfo);
        }
    }

    @Override
    public ProjectClassInfo find(String className) {
        if (className == null) {
            return null;
        }
        ProjectClassInfo exact = byQualifiedName.get(className);
        if (exact != null) {
            return exact;
        }
        List<ProjectClassInfo> candidates = bySimpleName.getOrDefault(className, List.of());
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    @Override
    public boolean contains(String className) {
        return find(className) != null;
    }

    @Override
    public void clear() {
        byQualifiedName.clear();
        bySimpleName.clear();
        byPackage.clear();
    }

    @Override
    public Optional<ProjectClassInfo> findByQualifiedName(String qualifiedName) {
        return Optional.ofNullable(byQualifiedName.get(qualifiedName));
    }

    @Override
    public List<ProjectClassInfo> findBySimpleName(String simpleName) {
        return Collections.unmodifiableList(bySimpleName.getOrDefault(simpleName, List.of()));
    }

    @Override
    public Collection<ProjectClassInfo> all() {
        return Collections.unmodifiableCollection(byQualifiedName.values());
    }

    @Override
    public Set<String> packages() {
        return Collections.unmodifiableSet(byPackage.keySet());
    }

    @Override
    public List<ProjectClassInfo> classesInPackage(String packageName) {
        return Collections.unmodifiableList(byPackage.getOrDefault(packageName, List.of()));
    }
}
