package com.anvith.archmorph.analysis.diagram;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import org.springframework.stereotype.Service;

@Service
public class PlantUmlDiagramGenerator
        implements DiagramGenerator {

    @Override
    public DiagramReport generate(
            DependencyGraph graph) {

        StringBuilder builder =
                new StringBuilder();

        builder.append("@startuml\n\n");

        for (DependencyEdge edge : graph.getEdges()) {

            builder.append(edge.getSource().getClassName())
                    .append(" --> ")
                    .append(edge.getTarget().getClassName())
                    .append("\n");
        }

        builder.append("\n@enduml");

        DiagramReport report =
                new DiagramReport();

        report.setPlantUml(
                builder.toString()
        );

        return report;
    }

}