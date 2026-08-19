package com.anvith.archmorph.analysis.dependency;

import com.anvith.archmorph.analysis.registry.ProjectClassInfo;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import com.anvith.archmorph.analysis.dependency.util.DependencyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DependencyNodeFactory {

    private final ProjectClassRegistry projectClassRegistry;

    /**
     * Creates a DependencyNode using registry metadata.
     */
    public DependencyNode createNode(String className) {

        ProjectClassInfo classInfo =
                projectClassRegistry.find(className);

        if (classInfo != null) {

            return new DependencyNode(
                    classInfo.getClassName(),
                    classInfo.getPackageName(),
                    classInfo.getComponentType()
            );
        }

        DependencyNode node = new DependencyNode();
        node.setClassName(className);

        return node;
    }

    /**
     * Connect source -> target.
     */
    public void connect(
            DependencyGraph graph,
            DependencyNode sourceNode,
            String dependency,
            DependencyType dependencyType) {

        if (DependencyUtils.shouldIgnore(dependency)) {
            return;
        }

        DependencyNode targetNode =
                createNode(dependency);

        graph.addNode(targetNode);

        graph.addEdge(
                new DependencyEdge(
                        sourceNode,
                        targetNode,
                        dependencyType
                )
        );
    }

}