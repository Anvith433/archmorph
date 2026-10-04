package com.anvith.archmorph.analysis.dependency;

import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.support.Projects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyExtractionTest {

    @TempDir
    Path temp;

    @Test
    void sameSimpleNameInDifferentPackagesIsResolvedThroughImports() throws Exception {
        ProjectModel model = Projects.model(temp, Map.of(
                "com/demo/user/User.java", "package com.demo.user; public class User {}",
                "com/demo/admin/User.java", "package com.demo.admin; public class User {}",
                "com/demo/user/UserService.java", "package com.demo.user; public class UserService { private User user; }",
                "com/demo/admin/AdminService.java",
                "package com.demo.admin; import com.demo.user.UserService; public class AdminService { User admin; UserService users; }"));
        DependencyGraph graph = Projects.graph(model);

        assertThat(edge(graph, "com.demo.user.UserService", "com.demo.user.User", DependencyType.FIELD)).isTrue();
        assertThat(edge(graph, "com.demo.admin.AdminService", "com.demo.admin.User", DependencyType.FIELD)).isTrue();
        assertThat(edge(graph, "com.demo.admin.AdminService", "com.demo.user.User", DependencyType.FIELD)).isFalse();
        assertThat(edge(graph, "com.demo.admin.AdminService", "com.demo.user.UserService", DependencyType.FIELD)).isTrue();
        assertThat(graph.getNodeCount()).isEqualTo(4);
        assertThat(graph.findNode("User")).as("ambiguous simple name").isNull();
    }

    @Test
    void lombokGettersAndRecordAccessorsAreFollowed() throws Exception {
        ProjectModel model = Projects.model(temp, Map.of(
                "com/shop/Customer.java", "package com.shop; import lombok.Data; @Data public class Customer { private String name; private Address address; }",
                "com/shop/Address.java", "package com.shop.geo; public class Address { }".replace("com.shop.geo", "com.shop"),
                "com/shop/Order.java", "package com.shop; import lombok.Getter; public class Order { @Getter private Customer customer; private Customer hidden; }",
                "com/shop/Line.java", "package com.shop; public record Line(Order order, int quantity) { }",
                "com/shop/Report.java", """
                        package com.shop;
                        public class Report {
                            public String of(Order order, Line line) {
                                return order.getCustomer().getAddress() + " " + line.order().getCustomer().getName();
                            }
                        }"""));
        DependencyGraph graph = Projects.graph(model);

        // order.getCustomer() (field-level @Getter) → Customer; .getAddress() (class-level @Data) → Address
        assertThat(edge(graph, "com.shop.Report", "com.shop.Customer", DependencyType.METHOD_INVOCATION)).isTrue();
        assertThat(edge(graph, "com.shop.Report", "com.shop.Address", DependencyType.METHOD_INVOCATION)).isFalse(); // getAddress() is the last call: no further call on Address
        // line.order() (record accessor) → Order
        assertThat(edge(graph, "com.shop.Report", "com.shop.Order", DependencyType.METHOD_INVOCATION)).isTrue();
    }

    @Test
    void classesNamedInMapStructExpressionsAreDependencies() throws Exception {
        ProjectModel model = Projects.model(temp, Map.of(
                "com/shop/util/Totals.java", "package com.shop.util; public final class Totals { public static String format(Object o) { return \"\"; } }",
                "com/shop/mapper/OrderMapper.java", """
                        package com.shop.mapper;
                        import org.mapstruct.Mapper;
                        import org.mapstruct.Mapping;
                        @Mapper
                        public interface OrderMapper {
                            @Mapping(target = "total", expression = "java(com.shop.util.Totals.format(order))")
                            @Mapping(target = "label", constant = "not java(com.shop.util.Totals)")
                            String toDto(Object order);
                        }"""));
        DependencyGraph graph = Projects.graph(model);
        assertThat(edge(graph, "com.shop.mapper.OrderMapper", "com.shop.util.Totals", DependencyType.TYPE_REFERENCE)).isTrue();
    }

    @Test
    void extractsEveryDependencyKind() throws Exception {
        ProjectModel model = Projects.model(temp, Map.of(
                "com/x/Base.java", "package com.x; public class Base {}",
                "com/x/Api.java", "package com.x; public interface Api {}",
                "com/x/Marker.java", "package com.x; public @interface Marker { Class<?> value(); }",
                "com/x/Item.java", "package com.x; public class Item { public static final int MAX = 1; }",
                "com/x/Repo.java", "package com.x; public class Repo { public Item find() { return null; } }",
                "com/x/Order.java", "package com.x; import jakarta.persistence.*; public class Order { @OneToMany private java.util.List<Item> items; }",
                "com/x/Service.java", """
                        package com.x;
                        import java.util.*;
                        @Marker(Item.class)
                        public class Service extends Base implements Api {
                            private final Repo repo;
                            public Service(Repo repo) { this.repo = repo; }
                            public Optional<Order> order(Map<String, Item> items) {
                                Item item = repo.find();
                                Object o = new Order();
                                int max = Item.MAX;
                                return Optional.empty();
                            }
                        }
                        """));
        DependencyGraph graph = Projects.graph(model);
        String s = "com.x.Service";

        assertThat(edge(graph, s, "com.x.Base", DependencyType.INHERITANCE)).isTrue();
        assertThat(edge(graph, s, "com.x.Api", DependencyType.IMPLEMENTATION)).isTrue();
        assertThat(edge(graph, s, "com.x.Repo", DependencyType.FIELD)).isTrue();
        assertThat(edge(graph, s, "com.x.Repo", DependencyType.CONSTRUCTOR)).isTrue();
        assertThat(edge(graph, s, "com.x.Repo", DependencyType.METHOD_INVOCATION)).isTrue();
        assertThat(edge(graph, s, "com.x.Order", DependencyType.GENERIC)).as("Optional<Order>").isTrue();
        assertThat(edge(graph, s, "com.x.Item", DependencyType.GENERIC)).as("Map<String, Item>").isTrue();
        assertThat(edge(graph, s, "com.x.Order", DependencyType.OBJECT_CREATION)).isTrue();
        assertThat(edge(graph, s, "com.x.Item", DependencyType.TYPE_REFERENCE)).isTrue();
        assertThat(edge(graph, s, "com.x.Marker", DependencyType.ANNOTATION)).isTrue();
        assertThat(edge(graph, s, "com.x.Item", DependencyType.ANNOTATION)).as("class literal in annotation").isTrue();
        assertThat(edge(graph, "com.x.Order", "com.x.Item", DependencyType.ENTITY_RELATIONSHIP)).isTrue();

        DependencyEdge field = graph.getEdges().stream()
                .filter(e -> e.getSource().getId().equals(s) && e.getDependencyType() == DependencyType.FIELD).findFirst().orElseThrow();
        assertThat(field.getLocation().file()).isEqualTo("src/main/java/com/x/Service.java");
        assertThat(field.getLocation().line()).isEqualTo(5);
        assertThat(field.getConfidence()).isGreaterThan(0.9);
    }

    @Test
    void jdkAndFrameworkTypesAreNotNodes() throws Exception {
        ProjectModel model = Projects.model(temp, Map.of("com/x/A.java",
                "package com.x; import java.util.List; import org.springframework.stereotype.Service; @Service public class A { List<String> names; }"));
        assertThat(Projects.graph(model).getEdgeCount()).isZero();
    }

    private static boolean edge(DependencyGraph graph, String source, String target, DependencyType type) {
        return graph.getEdges().stream().anyMatch(e -> e.getSource().getId().equals(source)
                && e.getTarget().getId().equals(target) && e.getDependencyType() == type);
    }
}
