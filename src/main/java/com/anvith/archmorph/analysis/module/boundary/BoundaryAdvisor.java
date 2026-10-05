package com.anvith.archmorph.analysis.module.boundary;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.module.editing.ModuleEdit;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentType;
import com.anvith.archmorph.parser.FieldInfo;
import com.anvith.archmorph.parser.MethodInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Finds cycles between modules and proposes how to break them.
 *
 * <p>Moving packages cannot remove a cycle that exists in the code; this advisor makes the remaining design
 * work concrete. It repeatedly picks, inside each group of mutually dependent modules, the lightest module
 * dependency (fewest and weakest class-level dependencies) and explains how to remove it:</p>
 * <ol>
 *   <li><b>Move a class</b> when one class causes the whole dependency and moving it to the other module,
 *       simulated on the dependency graph, separates the two modules without creating a new cycle. This is
 *       the only suggestion that can be applied automatically (as a module edit).</li>
 *   <li><b>Split a facade</b> when the dependency comes from a class reaching into three or more modules;
 *       its methods are grouped by the module whose types they use.</li>
 *   <li><b>Make a relationship unidirectional</b> when the dependency is only between JPA entities.</li>
 *   <li><b>Invert the dependency</b> otherwise: an event published by one module and handled by the other,
 *       or an interface owned by the calling module.</li>
 * </ol>
 * The analysis is deterministic and read-only.
 */
@Service
public class BoundaryAdvisor {

    private static final Set<DependencyType> DATA_KINDS = EnumSet.of(DependencyType.FIELD, DependencyType.ENTITY_RELATIONSHIP,
            DependencyType.GENERIC, DependencyType.METHOD_RETURN, DependencyType.METHOD_PARAMETER,
            DependencyType.TYPE_REFERENCE, DependencyType.METHOD_INVOCATION, DependencyType.OBJECT_CREATION);
    private static final Set<String> INVERSE_SIDE = Set.of("OneToMany", "ManyToMany");
    private static final Set<String> OWNING_SIDE = Set.of("ManyToOne", "OneToOne", "JoinColumn");
    private static final int MAX_EVIDENCE = 8;

    public BoundaryReport advise(ModuleDiscoveryReport modules, DependencyGraph graph, Map<String, ClassMetadata> facts) {
        Map<String, String> moduleOf = moduleIndex(modules);
        List<List<String>> cycles = cyclicComponents(moduleGraph(graph, moduleOf, Set.of()));
        if (cycles.isEmpty()) {
            return BoundaryReport.empty();
        }

        // Greedy: break the lightest module dependency of a cycle, then look again. Accepted moves change the
        // working assignment, so later suggestions build on earlier ones instead of contradicting them.
        List<BoundarySuggestion> suggestions = new ArrayList<>();
        Map<String, String> working = new HashMap<>(moduleOf);
        Set<String> broken = new HashSet<>();
        Set<String> movedClasses = new HashSet<>();
        Set<String> splitFacades = new HashSet<>();
        int guard = 0;
        while (guard++ < 200) {
            Map<String, Map<String, List<DependencyEdge>>> current = moduleGraph(graph, without(working, splitFacades), broken);
            List<List<String>> components = cyclicComponents(current);
            if (components.isEmpty()) {
                break;
            }
            Set<String> component = new HashSet<>(components.getFirst());
            // 1. a facade inside the cycle is split first: it explains most of the cycle
            // 2. a misplaced class is the cheapest fix (no code change), so look for one across the whole cycle
            BoundarySuggestion suggestion = facadeInCycle(current, component, graph, facts, working, splitFacades);
            if (suggestion == null) {
                suggestion = anyMove(current, component, modules, graph, working, broken, movedClasses);
            }
            String[] lightest = null;
            if (suggestion == null) {
                lightest = lightestEdge(current, component, facts);
                if (lightest == null) {
                    break;
                }
                List<DependencyEdge> edges = current.get(lightest[0]).get(lightest[1]);
                suggestion = suggest(lightest[0], lightest[1], edges, modules, graph, facts, working, broken, movedClasses);
            }
            suggestions.add(suggestion);
            if (suggestion.kind() == SuggestionKind.MOVE_CLASS && suggestion.edit() != null) {
                working.put(suggestion.edit().className(), suggestion.edit().target());
                movedClasses.add(suggestion.edit().className());
            } else if (suggestion.kind() == SuggestionKind.SPLIT_FACADE) {
                // once split, the facade (and its interface / implementation) no longer connects the modules
                splitFacades.addAll(facadeFamily(suggestion.subject(), graph, working));
            } else if (lightest != null) {
                broken.add(lightest[0] + "->" + lightest[1]);
            }
        }

        List<BoundarySuggestion> numbered = new ArrayList<>();
        suggestions.sort(Comparator.comparing(BoundarySuggestion::from).thenComparing(BoundarySuggestion::to));
        for (int i = 0; i < suggestions.size(); i++) {
            BoundarySuggestion s = suggestions.get(i);
            numbered.add(s.withId(String.format("b-%03d", i + 1)));
        }
        return new BoundaryReport(cycles, numbered);
    }

    // ================================================================== suggestions

    private BoundarySuggestion suggest(String from, String to, List<DependencyEdge> edges, ModuleDiscoveryReport modules,
                                       DependencyGraph graph, Map<String, ClassMetadata> facts, Map<String, String> moduleOf,
                                       Set<String> broken, Set<String> movedClasses) {
        List<String> evidence = evidence(edges);

        // a facade on either side: splitting it is the fix, moving it would only shift the hub
        DependencyNode facade = null;
        int widest = 0;
        for (DependencyNode node : endpoints(edges)) {
            int reach = reachedModules(node, graph, moduleOf).size();
            boolean controller = node.getComponentType() == ComponentType.CONTROLLER;
            if (reach >= 3 && facts.containsKey(node.getId()) && !controller && reach > widest) {
                facade = node;
                widest = reach;
            }
        }
        if (facade != null) {
            List<DependencyEdge> through = crossModuleEdges(facadeFamily(facade.getId(), graph, moduleOf), graph, moduleOf);
            return facadeSuggestion(moduleOf.get(facade.getId()), from, to, through, facade,
                    reachedModules(facade, graph, moduleOf), facts, moduleOf, evidence(through));
        }
        BoundarySuggestion move = moveSuggestion(from, to, edges, modules, graph, moduleOf, broken, movedClasses, evidence);
        if (move != null) {
            return move;
        }
        boolean hasEntityLink = edges.stream().anyMatch(e -> isEntity(e.getSource()) && isEntity(e.getTarget())
                && DATA_KINDS.contains(e.getDependencyType()));
        if (hasEntityLink) {
            return relationshipSuggestion(from, to, edges, facts, evidence);
        }
        return inversionSuggestion(from, to, edges, evidence);
    }

    /** Every dependency between a class of {@code family} and a class of another module, in either direction. */
    private static List<DependencyEdge> crossModuleEdges(Set<String> family, DependencyGraph graph, Map<String, String> moduleOf) {
        List<DependencyEdge> result = new ArrayList<>();
        for (DependencyEdge edge : graph.getEdges()) {
            String source = edge.getSource().getId();
            String target = edge.getTarget().getId();
            boolean touches = family.contains(source) != family.contains(target);
            String a = moduleOf.get(source);
            String b = moduleOf.get(target);
            if (touches && a != null && b != null && !a.equals(b)) {
                result.add(edge);
            }
        }
        result.sort(Comparator.comparing((DependencyEdge e) -> e.getSource().getId()).thenComparing(e -> e.getTarget().getId())
                .thenComparing(e -> e.getDependencyType().name()));
        return result;
    }

    private BoundarySuggestion facadeInCycle(Map<String, Map<String, List<DependencyEdge>>> current, Set<String> component,
                                             DependencyGraph graph, Map<String, ClassMetadata> facts,
                                             Map<String, String> moduleOf, Set<String> splitFacades) {
        DependencyNode facade = null;
        int widest = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(moduleOf).entrySet()) {
            if (!component.contains(entry.getValue()) || splitFacades.contains(entry.getKey()) || !facts.containsKey(entry.getKey())) {
                continue;
            }
            DependencyNode node = graph.findNode(entry.getKey());
            if (node == null || node.getComponentType() == ComponentType.CONTROLLER) {
                continue;
            }
            int reach = reachedModules(node, graph, moduleOf).size();
            if (reach >= 3 && reach > widest) {
                facade = node;
                widest = reach;
            }
        }
        if (facade == null) {
            return null;
        }
        List<DependencyEdge> through = crossModuleEdges(facadeFamily(facade.getId(), graph, moduleOf), graph, moduleOf);
        String home = moduleOf.get(facade.getId());
        return facadeSuggestion(home, home, home, through, facade, reachedModules(facade, graph, moduleOf), facts, moduleOf,
                evidence(through));
    }

    private BoundarySuggestion anyMove(Map<String, Map<String, List<DependencyEdge>>> current, Set<String> component,
                                       ModuleDiscoveryReport modules, DependencyGraph graph, Map<String, String> moduleOf,
                                       Set<String> broken, Set<String> movedClasses) {
        for (String a : new TreeSet<>(component)) {
            for (Map.Entry<String, List<DependencyEdge>> target : current.getOrDefault(a, Map.of()).entrySet()) {
                if (!component.contains(target.getKey()) || target.getValue().isEmpty() || intended(target.getKey())) {
                    continue;
                }
                BoundarySuggestion move = moveSuggestion(a, target.getKey(), target.getValue(), modules, graph, moduleOf,
                        broken, movedClasses, evidence(target.getValue()));
                if (move != null) {
                    return move;
                }
            }
        }
        return null;
    }

    private static Map<String, String> without(Map<String, String> moduleOf, Set<String> classes) {
        if (classes.isEmpty()) {
            return moduleOf;
        }
        Map<String, String> copy = new HashMap<>(moduleOf);
        classes.forEach(copy::remove);
        return copy;
    }

    /** The facade plus the types it implements or that implement it within the same module. */
    private static Set<String> facadeFamily(String facadeId, DependencyGraph graph, Map<String, String> moduleOf) {
        Set<String> family = new HashSet<>();
        family.add(facadeId);
        DependencyNode node = graph.findNode(facadeId);
        if (node == null) {
            return family;
        }
        String module = moduleOf.get(facadeId);
        for (DependencyEdge edge : graph.getOutgoingEdges(node)) {
            if (EnumSet.of(DependencyType.INHERITANCE, DependencyType.IMPLEMENTATION).contains(edge.getDependencyType())
                    && module != null && module.equals(moduleOf.get(edge.getTarget().getId()))) {
                family.add(edge.getTarget().getId());
            }
        }
        for (DependencyEdge edge : graph.getIncomingEdges(node)) {
            if (EnumSet.of(DependencyType.INHERITANCE, DependencyType.IMPLEMENTATION).contains(edge.getDependencyType())
                    && module != null && module.equals(moduleOf.get(edge.getSource().getId()))) {
                family.add(edge.getSource().getId());
            }
        }
        return family;
    }

    private static List<DependencyNode> endpoints(List<DependencyEdge> edges) {
        Map<String, DependencyNode> nodes = new java.util.TreeMap<>();
        edges.forEach(e -> {
            nodes.putIfAbsent(e.getSource().getId(), e.getSource());
            nodes.putIfAbsent(e.getTarget().getId(), e.getTarget());
        });
        return new ArrayList<>(nodes.values());
    }

    /** One class causes the dependency and belongs to the other module more than to its own. */
    private BoundarySuggestion moveSuggestion(String from, String to, List<DependencyEdge> edges, ModuleDiscoveryReport modules,
                                              DependencyGraph graph, Map<String, String> moduleOf, Set<String> broken,
                                              Set<String> movedClasses, List<String> evidence) {
        List<String[]> candidates = new ArrayList<>(); // {class id, current module, target module}
        List<DependencyNode> sources = sources(edges);
        if (sources.size() == 1) {
            candidates.add(new String[]{sources.getFirst().getId(), from, to});
        }
        Set<DependencyNode> targets = new TreeSet<>(Comparator.comparing(DependencyNode::getId));
        edges.forEach(e -> targets.add(e.getTarget()));
        if (targets.size() == 1) {
            candidates.add(new String[]{targets.iterator().next().getId(), to, from});
        }
        int before = cyclicModuleCount(moduleGraph(graph, moduleOf, broken));
        for (String[] candidate : candidates) {
            String id = candidate[0];
            ModuleInfo destination = modules.getModule(candidate[2]);
            ClassAssignment assignment = modules.getAssignment(id);
            if (destination == null || !destination.isBusinessModule() || (assignment != null && assignment.locked())
                    || movedClasses.contains(id)) {
                continue;
            }
            DependencyNode node = graph.findNode(id);
            if (node == null || node.getComponentType() == ComponentType.APPLICATION
                    || reachedModules(node, graph, moduleOf).size() >= 3) {
                continue;
            }
            int towardsDestination = connections(node, graph, moduleOf, candidate[2]);
            int towardsOwn = connections(node, graph, moduleOf, candidate[1]);
            if (towardsDestination <= towardsOwn) {
                continue; // only classes that clearly belong to the other module
            }
            Map<String, String> simulated = new HashMap<>(moduleOf);
            simulated.put(id, candidate[2]);
            Map<String, Map<String, List<DependencyEdge>>> after = moduleGraph(graph, simulated, broken);
            boolean separated = cyclicComponents(after).stream().noneMatch(c -> c.contains(from) && c.contains(to));
            if (!separated || cyclicModuleCount(after) > before) {
                continue;
            }
            String className = node.getClassName();
            return new BoundarySuggestion(null, SuggestionKind.MOVE_CLASS, from, to, id,
                    "Move " + className + " to module '" + candidate[2] + "'",
                    className + " has " + towardsDestination + " dependencies with module '" + candidate[2] + "' and "
                            + towardsOwn + " with '" + candidate[1] + "'. Moving it removes the dependency of '" + from
                            + "' on '" + to + "' without creating a new cycle (simulated on the dependency graph).",
                    edges.size(),
                    List.of("Move " + className + " from '" + candidate[1] + "' to '" + candidate[2] + "'.",
                            "This only changes packages and imports; ArchMorph can apply it as a module decision."),
                    evidence,
                    new ModuleEdit(ModuleEdit.Type.MOVE_CLASS, null, null, candidate[2], null, id, null));
        }
        return null;
    }

    private BoundarySuggestion facadeSuggestion(String home, String from, String to, List<DependencyEdge> edges,
                                                DependencyNode facade, Set<String> reached, Map<String, ClassMetadata> facts,
                                                Map<String, String> moduleOf, List<String> evidence) {
        ClassMetadata metadata = facts.get(facade.getId());
        Map<String, String> moduleBySimpleName = new HashMap<>();
        moduleOf.forEach((id, module) -> moduleBySimpleName.putIfAbsent(id.substring(id.lastIndexOf('.') + 1), module));
        Map<String, Set<String>> methodsByModule = new TreeMap<>();
        for (MethodInfo method : metadata.getMethodDetails()) {
            if (method.constructor() || method.modifiers().contains("private")) {
                continue;
            }
            Set<String> types = new TreeSet<>();
            types.add(method.returnType());
            method.parameters().forEach(p -> types.add(p.type()));
            String owner = null;
            for (String type : types) {
                for (String token : type.split("[^A-Za-z0-9_$]+")) {
                    String module = moduleBySimpleName.get(token);
                    if (module != null && !module.equals(home) && !"shared".equals(module)) {
                        owner = module;
                        break;
                    }
                }
                if (owner != null) {
                    break;
                }
            }
            methodsByModule.computeIfAbsent(owner == null ? "(no single module)" : owner, k -> new TreeSet<>()).add(method.name());
        }
        List<String> steps = new ArrayList<>();
        steps.add(facade.getClassName() + " in module '" + home + "' reaches into " + reached + ".");
        methodsByModule.forEach((module, methods) -> steps.add(module.startsWith("(")
                ? "Methods that use several modules or none: " + String.join(", ", methods) + " — keep them in an application service or split them by hand."
                : "Move " + String.join(", ", methods) + " to a service in module '" + module + "' (its public API)."));
        steps.add("Let each module's controllers call their own module's service; keep " + facade.getClassName()
                + " as a thin delegate until every caller is migrated, then delete it.");
        return new BoundarySuggestion(null, SuggestionKind.SPLIT_FACADE, home, String.join(", ", reached), facade.getId(),
                "Split the facade " + facade.getClassName() + " by module",
                "A class that serves every module makes all of them depend on each other through it.",
                edges.size(), steps, evidence, null);
    }

    private BoundarySuggestion relationshipSuggestion(String from, String to, List<DependencyEdge> edges,
                                                      Map<String, ClassMetadata> facts, List<String> evidence) {
        List<String> steps = new ArrayList<>();
        Set<String> targetNames = new TreeSet<>();
        edges.forEach(e -> targetNames.add(e.getTarget().getClassName()));
        Map<String, Set<String>> otherUsers = new TreeMap<>();
        for (DependencyEdge edge : edges) {
            if (!isEntity(edge.getSource())) {
                otherUsers.computeIfAbsent(edge.getSource().getClassName(), k -> new TreeSet<>()).add(edge.getTarget().getClassName());
            }
        }
        for (DependencyNode source : sources(edges)) {
            ClassMetadata metadata = facts.get(source.getId());
            if (metadata == null || !isEntity(source)) {
                continue;
            }
            for (FieldInfo field : metadata.getFieldDetails()) {
                if (!mentionsAny(field.type(), targetNames)) {
                    continue;
                }
                boolean inverse = field.annotations().stream().anyMatch(INVERSE_SIDE::contains);
                boolean owning = field.annotations().stream().anyMatch(OWNING_SIDE::contains);
                String where = source.getClassName() + "." + field.name() + " (" + field.type() + ")";
                if (inverse) {
                    steps.add("Remove the inverse side " + where + " and its accessors; the other entity keeps the owning "
                            + "@ManyToOne side, so the database mapping does not change.");
                    steps.add("Where the collection was read, ask module '" + to + "' instead (e.g. a repository query "
                            + "such as findBy" + source.getClassName() + "Id exposed through its public API).");
                } else if (owning) {
                    steps.add("Replace the reference " + where + " with the identifier of the "
                            + field.type().replaceAll(".*[<]|[>].*", "") + " (a plain id column); load the entity "
                            + "through module '" + to + "' when it is needed.");
                } else {
                    steps.add("Replace the reference " + where + " with an identifier or a value object owned by '" + from + "'.");
                }
            }
        }
        if (steps.isEmpty()) {
            steps.add("Keep the association on one side only and reach the other side through a query in its module.");
        }
        otherUsers.forEach((user, used) -> steps.add(user + " in '" + from + "' also uses " + used + " of '" + to
                + "': move that logic to '" + to + "' or reach it through an interface owned by '" + from + "'."));
        return new BoundarySuggestion(null, SuggestionKind.UNIDIRECTIONAL_RELATIONSHIP, from, to, null,
                "Make the " + from + " -> " + to + " entity relationship one-directional",
                "Entities of '" + from + "' and '" + to + "' reference each other. A bidirectional association ties "
                        + "the two modules together; one direction is enough for the mapping.",
                edges.size(), steps, evidence, null);
    }

    private BoundarySuggestion inversionSuggestion(String from, String to, List<DependencyEdge> edges, List<String> evidence) {
        Set<String> used = new TreeSet<>();
        Set<String> users = new TreeSet<>();
        edges.forEach(e -> {
            used.add(e.getTarget().getClassName());
            users.add(e.getSource().getClassName());
        });
        List<String> steps = List.of(
                users + " in module '" + from + "' use " + used + " of module '" + to + "'.",
                "If '" + from + "' only notifies '" + to + "' that something happened, publish a domain event from '" + from
                        + "' (ApplicationEventPublisher) and handle it in '" + to + "' (@EventListener or Spring Modulith's "
                        + "@ApplicationModuleListener). The dependency then points from '" + to + "' to '" + from + "' only.",
                "If '" + from + "' needs an answer, declare an interface in '" + from + "' describing what it needs and "
                        + "implement it in '" + to + "' (dependency inversion).");
        return new BoundarySuggestion(null, SuggestionKind.INVERT_DEPENDENCY, from, to, null,
                "Invert the dependency of '" + from + "' on '" + to + "'",
                "This is the lightest dependency in the cycle (" + edges.size() + " class-level dependencies).",
                edges.size(), steps, evidence, null);
    }

    // ================================================================== graph helpers

    private static Map<String, String> moduleIndex(ModuleDiscoveryReport modules) {
        Map<String, String> index = new HashMap<>();
        for (ModuleInfo module : modules.getModules()) {
            if (ModuleDiscoveryReport.APPLICATION.equals(module.getModuleName())) {
                continue; // application wiring belongs to no module
            }
            module.getClasses().forEach(n -> index.put(n.getId(), module.getModuleName()));
        }
        return index;
    }

    private static Map<String, Map<String, List<DependencyEdge>>> moduleGraph(DependencyGraph graph, Map<String, String> moduleOf,
                                                                       Set<String> broken) {
        Map<String, Map<String, List<DependencyEdge>>> result = new TreeMap<>();
        for (DependencyEdge edge : graph.getEdges()) {
            String a = moduleOf.get(edge.getSource().getId());
            String b = moduleOf.get(edge.getTarget().getId());
            if (a == null || b == null || a.equals(b) || broken.contains(a + "->" + b)) {
                continue;
            }
            result.computeIfAbsent(a, k -> new TreeMap<>()).computeIfAbsent(b, k -> new ArrayList<>()).add(edge);
        }
        result.values().forEach(targets -> targets.values().forEach(list ->
                list.sort(Comparator.comparing((DependencyEdge e) -> e.getSource().getId())
                        .thenComparing(e -> e.getTarget().getId()).thenComparing(e -> e.getDependencyType().name()))));
        return result;
    }

    /** Strongly connected components with two or more modules (Tarjan), each sorted, sorted by first name. */
    static List<List<String>> cyclicComponents(Map<String, Map<String, List<DependencyEdge>>> graph) {
        Set<String> nodes = new TreeSet<>(graph.keySet());
        graph.values().forEach(t -> nodes.addAll(t.keySet()));
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        List<List<String>> components = new ArrayList<>();
        int[] counter = {0};
        for (String node : nodes) {
            if (!index.containsKey(node)) {
                strongConnect(node, graph, index, low, stack, onStack, components, counter);
            }
        }
        components.removeIf(c -> c.size() < 2);
        components.forEach(c -> c.sort(String::compareTo));
        components.sort(Comparator.comparing(c -> c.getFirst()));
        return components;
    }

    private static void strongConnect(String node, Map<String, Map<String, List<DependencyEdge>>> graph, Map<String, Integer> index,
                                      Map<String, Integer> low, Deque<String> stack, Set<String> onStack,
                                      List<List<String>> components, int[] counter) {
        index.put(node, counter[0]);
        low.put(node, counter[0]);
        counter[0]++;
        stack.push(node);
        onStack.add(node);
        for (String next : graph.getOrDefault(node, Map.of()).keySet()) {
            if (graph.get(node).get(next).isEmpty()) {
                continue;
            }
            if (!index.containsKey(next)) {
                strongConnect(next, graph, index, low, stack, onStack, components, counter);
                low.put(node, Math.min(low.get(node), low.get(next)));
            } else if (onStack.contains(next)) {
                low.put(node, Math.min(low.get(node), index.get(next)));
            }
        }
        if (low.get(node).equals(index.get(node))) {
            List<String> component = new ArrayList<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));
            components.add(component);
        }
    }

    /**
     * A dependency on shared code is the intended direction and is never the one to cut: in a shared ↔ module cycle
     * the fix is to remove shared's dependency on the module.
     */
    private static boolean intended(String targetModule) {
        return ModuleDiscoveryReport.SHARED.equals(targetModule);
    }

    private static int cyclicModuleCount(Map<String, Map<String, List<DependencyEdge>>> graph) {
        return cyclicComponents(graph).stream().mapToInt(List::size).sum();
    }

    /** The module dependency inside the component with the smallest total weight (ties: fewer edges, then names). */
    private static String[] lightestEdge(Map<String, Map<String, List<DependencyEdge>>> graph, Set<String> component,
                                         Map<String, ClassMetadata> facts) {
        String[] best = null;
        double bestWeight = Double.MAX_VALUE;
        int bestCount = Integer.MAX_VALUE;
        for (String a : new TreeSet<>(component)) {
            for (Map.Entry<String, List<DependencyEdge>> target : graph.getOrDefault(a, Map.of()).entrySet()) {
                if (!component.contains(target.getKey()) || target.getValue().isEmpty() || intended(target.getKey())) {
                    continue;
                }
                double weight = target.getValue().stream().mapToDouble(e -> breakCost(e, facts)).sum();
                int count = target.getValue().size();
                if (weight < bestWeight - 1e-9 || (Math.abs(weight - bestWeight) < 1e-9 && count < bestCount)) {
                    best = new String[]{a, target.getKey()};
                    bestWeight = weight;
                    bestCount = count;
                }
            }
        }
        return best;
    }

    /**
     * Cost of removing one class-level dependency: its weight, but the inverse side of a JPA association
     * (a {@code @OneToMany}/{@code @ManyToMany} collection) is cheap — removing it leaves the mapping intact.
     */
    private static double breakCost(DependencyEdge edge, Map<String, ClassMetadata> facts) {
        double weight = edge.getWeight();
        if (isEntity(edge.getSource()) && isEntity(edge.getTarget())) {
            ClassMetadata source = facts.get(edge.getSource().getId());
            if (source != null && source.getFieldDetails().stream().anyMatch(f -> mentionsAny(f.type(), Set.of(edge.getTarget().getClassName()))
                    && f.annotations().stream().anyMatch(INVERSE_SIDE::contains))) {
                return weight * 0.25;
            }
        }
        return weight;
    }

    private static List<DependencyNode> sources(List<DependencyEdge> edges) {
        Map<String, DependencyNode> sources = new LinkedHashMap<>();
        edges.forEach(e -> sources.putIfAbsent(e.getSource().getId(), e.getSource()));
        return new ArrayList<>(sources.values());
    }

    private static Set<String> reachedModules(DependencyNode node, DependencyGraph graph, Map<String, String> moduleOf) {
        String own = moduleOf.get(node.getId());
        Set<String> reached = new TreeSet<>();
        for (DependencyNode target : graph.getSuccessors(node)) {
            String module = moduleOf.get(target.getId());
            if (module != null && !module.equals(own) && !"shared".equals(module)) {
                reached.add(module);
            }
        }
        return reached;
    }

    private static int connections(DependencyNode node, DependencyGraph graph, Map<String, String> moduleOf, String module) {
        int count = 0;
        for (DependencyNode other : graph.getSuccessors(node)) {
            if (!other.getId().equals(node.getId()) && module.equals(moduleOf.get(other.getId()))) {
                count++;
            }
        }
        for (DependencyNode other : graph.getPredecessors(node)) {
            if (!other.getId().equals(node.getId()) && module.equals(moduleOf.get(other.getId()))) {
                count++;
            }
        }
        return count;
    }

    private static boolean isEntity(DependencyNode node) {
        return node.getComponentType() == ComponentType.ENTITY;
    }

    private static boolean mentionsAny(String type, Set<String> names) {
        for (String token : type.split("[^A-Za-z0-9_$]+")) {
            if (names.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> evidence(List<DependencyEdge> edges) {
        List<String> lines = new ArrayList<>();
        for (DependencyEdge edge : edges) {
            if (lines.size() == MAX_EVIDENCE) {
                lines.add("… and " + (edges.size() - MAX_EVIDENCE) + " more");
                break;
            }
            lines.add(edge.getSource().getClassName() + " → " + edge.getTarget().getClassName() + " ("
                    + edge.getDependencyType().name().toLowerCase().replace('_', ' ')
                    + (edge.getLocation() == null ? "" : ", " + edge.getLocation().file() + ":" + edge.getLocation().line()) + ")");
        }
        return lines;
    }
}
