package com.anvith.archmorph.analysis.cycle;

import com.anvith.archmorph.analysis.architecture.Severity;
import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.parser.ComponentType;
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

/**
 * Finds dependency cycles as strongly connected components (Tarjan, iterative).
 *
 * <p>Each non-trivial SCC is reported once, with a representative closed path,
 * the dependency types involved, a severity and a recommendation. Cycles are
 * reported, never rewritten automatically: breaking a cycle is an
 * architectural decision.</p>
 */
@Service
public class DefaultCircularDependencyDetector implements CircularDependencyDetector {

    private static final Set<DependencyType> ENTITY_KINDS =
            EnumSet.of(DependencyType.ENTITY_RELATIONSHIP, DependencyType.GENERIC, DependencyType.FIELD,
                    DependencyType.METHOD_RETURN, DependencyType.METHOD_PARAMETER, DependencyType.TYPE_REFERENCE);

    @Override
    public CycleReport detect(DependencyGraph graph) {
        List<DependencyNode> nodes = new ArrayList<>(graph.getInternalNodes());
        nodes.sort(Comparator.comparing(DependencyNode::getId));

        List<List<DependencyNode>> components = stronglyConnectedComponents(graph, nodes);
        components.removeIf(c -> c.size() < 2);
        components.forEach(c -> c.sort(Comparator.comparing(DependencyNode::getId)));
        components.sort(Comparator.comparing(c -> c.getFirst().getId()));

        CycleReport report = new CycleReport();
        int index = 1;
        for (List<DependencyNode> component : components) {
            report.addCycle(describe(graph, component, String.format("cycle-%03d", index++)));
        }
        return report;
    }

    private List<List<DependencyNode>> stronglyConnectedComponents(DependencyGraph graph, List<DependencyNode> nodes) {
        Map<DependencyNode, Integer> indexOf = new HashMap<>();
        Map<DependencyNode, Integer> lowLink = new HashMap<>();
        Set<DependencyNode> onStack = new HashSet<>();
        Deque<DependencyNode> stack = new ArrayDeque<>();
        List<List<DependencyNode>> result = new ArrayList<>();
        int[] counter = {0};

        for (DependencyNode start : nodes) {
            if (indexOf.containsKey(start)) {
                continue;
            }
            Deque<Object[]> work = new ArrayDeque<>();
            work.push(new Object[]{start, sortedSuccessors(graph, start).iterator()});
            indexOf.put(start, counter[0]);
            lowLink.put(start, counter[0]++);
            stack.push(start);
            onStack.add(start);

            while (!work.isEmpty()) {
                Object[] frame = work.peek();
                DependencyNode node = (DependencyNode) frame[0];
                @SuppressWarnings("unchecked")
                java.util.Iterator<DependencyNode> successors = (java.util.Iterator<DependencyNode>) frame[1];
                if (successors.hasNext()) {
                    DependencyNode next = successors.next();
                    if (!indexOf.containsKey(next)) {
                        indexOf.put(next, counter[0]);
                        lowLink.put(next, counter[0]++);
                        stack.push(next);
                        onStack.add(next);
                        work.push(new Object[]{next, sortedSuccessors(graph, next).iterator()});
                    } else if (onStack.contains(next)) {
                        lowLink.put(node, Math.min(lowLink.get(node), indexOf.get(next)));
                    }
                } else {
                    work.pop();
                    if (!work.isEmpty()) {
                        DependencyNode parent = (DependencyNode) work.peek()[0];
                        lowLink.put(parent, Math.min(lowLink.get(parent), lowLink.get(node)));
                    }
                    if (lowLink.get(node).equals(indexOf.get(node))) {
                        List<DependencyNode> component = new ArrayList<>();
                        DependencyNode member;
                        do {
                            member = stack.pop();
                            onStack.remove(member);
                            component.add(member);
                        } while (!member.equals(node));
                        result.add(component);
                    }
                }
            }
        }
        return result;
    }

    private List<DependencyNode> sortedSuccessors(DependencyGraph graph, DependencyNode node) {
        List<DependencyNode> successors = new ArrayList<>(graph.getSuccessors(node));
        successors.removeIf(DependencyNode::isExternal);
        successors.sort(Comparator.comparing(DependencyNode::getId));
        return successors;
    }

    private DependencyCycle describe(DependencyGraph graph, List<DependencyNode> component, String id) {
        Set<DependencyNode> members = new HashSet<>(component);
        Set<DependencyType> types = EnumSet.noneOf(DependencyType.class);
        int edgeCount = 0;
        boolean upwardDependency = false;
        for (DependencyNode node : component) {
            for (DependencyEdge edge : graph.getOutgoingEdges(node)) {
                if (members.contains(edge.getTarget())) {
                    edgeCount++;
                    types.add(edge.getDependencyType());
                    upwardDependency |= rank(edge.getSource()) > rank(edge.getTarget())
                            && rank(edge.getTarget()) >= 0 && rank(edge.getSource()) >= 0;
                }
            }
        }

        boolean allEntities = component.stream().allMatch(n -> n.getComponentType() == ComponentType.ENTITY);
        Severity severity;
        String recommendation;
        if (allEntities && ENTITY_KINDS.containsAll(types)) {
            severity = Severity.LOW;
            recommendation = "Bidirectional entity relationship. Keep these entities in the same module, "
                    + "or make one side of the association unidirectional.";
        } else if (component.size() >= 4 || upwardDependency) {
            severity = Severity.HIGH;
            recommendation = "Cycle spans several classes or points from a lower layer to a higher one. "
                    + "Introduce an interface owned by the lower layer or publish events instead of direct calls. "
                    + "Requires an architectural decision; ArchMorph will not rewrite it.";
        } else {
            severity = Severity.MEDIUM;
            recommendation = "Extract the shared behaviour into a third class both can depend on, "
                    + "or invert one dependency through an interface.";
        }

        return new DependencyCycle(id,
                component.stream().map(DependencyNode::getId).toList(),
                component.stream().map(DependencyNode::getClassName).toList(),
                representativePath(graph, component, members),
                edgeCount, types, severity, recommendation);
    }

    /** Shortest closed path through the first member (BFS inside the component). */
    private List<String> representativePath(DependencyGraph graph, List<DependencyNode> component, Set<DependencyNode> members) {
        DependencyNode start = component.getFirst();
        Map<DependencyNode, DependencyNode> parent = new LinkedHashMap<>();
        Deque<DependencyNode> queue = new ArrayDeque<>();
        queue.add(start);
        DependencyNode closing = null;
        Set<DependencyNode> seen = new HashSet<>();
        seen.add(start);
        while (!queue.isEmpty() && closing == null) {
            DependencyNode current = queue.poll();
            for (DependencyNode next : sortedSuccessors(graph, current)) {
                if (!members.contains(next)) {
                    continue;
                }
                if (next.equals(start)) {
                    closing = current;
                    break;
                }
                if (seen.add(next)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        List<String> path = new ArrayList<>();
        if (closing == null) {
            component.forEach(n -> path.add(n.getClassName()));
            path.add(start.getClassName());
            return path;
        }
        Deque<String> reversed = new ArrayDeque<>();
        for (DependencyNode n = closing; n != null; n = parent.get(n)) {
            reversed.push(n.getClassName());
        }
        path.addAll(reversed);
        path.add(start.getClassName());
        return path;
    }

    /** Layer rank: controller 3, service 2, repository 1, entity 0; -1 for other roles. */
    private static int rank(DependencyNode node) {
        if (node.getComponentType() == null) {
            return -1;
        }
        return switch (node.getComponentType()) {
            case CONTROLLER -> 3;
            case SERVICE -> 2;
            case REPOSITORY -> 1;
            case ENTITY -> 0;
            default -> -1;
        };
    }
}
