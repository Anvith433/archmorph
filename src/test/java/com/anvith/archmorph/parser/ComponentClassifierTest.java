package com.anvith.archmorph.parser;

import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentClassifierTest {

    private final ComponentAnalyzer analyzer = new ComponentAnalyzer(new ComponentClassifier());

    private ClassMetadata analyze(String source) {
        return analyzer.analyze(parse(source));
    }

    private static CompilationUnit parse(String source) {
        return new JavaParserService().createParser().parse(source).getResult().orElseThrow();
    }

    @Test
    void annotationIsStrongestEvidenceAndAgreementRaisesConfidence() {
        ClassMetadata service = analyze("package com.demo.service; @Service public class UserService {}");

        assertThat(service.getComponentType()).isEqualTo(ComponentType.SERVICE);
        assertThat(service.getClassification().confidence()).isGreaterThan(0.95);
        assertThat(service.getClassification().evidence())
                .containsSubsequence("@Service", "class name ends with Service", "package segment 'service'");
    }

    @Test
    void inheritanceBeatsGenericComponentAnnotation() {
        ClassMetadata filter = analyze("package com.demo.web; @Component public class AuthFilter extends OncePerRequestFilter {}");
        assertThat(filter.getComponentType()).isEqualTo(ComponentType.FILTER);
        assertThat(filter.getClassification().evidence().getFirst()).contains("OncePerRequestFilter");
    }

    @Test
    void securityAnnotationWinsOverConfiguration() {
        ClassMetadata config = analyze("package com.demo.config; @Configuration @EnableWebSecurity public class SecurityConfig {}");
        assertThat(config.getComponentType()).isEqualTo(ComponentType.SECURITY);
    }

    @Test
    void contradictingEvidenceLowersConfidence() {
        ClassMetadata odd = analyze("package com.demo.controller; @Repository public class UserController {}");
        assertThat(odd.getComponentType()).isEqualTo(ComponentType.REPOSITORY);
        assertThat(odd.getClassification().confidence()).isLessThan(0.95);
        assertThat(odd.getClassification().evidence()).anyMatch(e -> e.contains("suggests CONTROLLER"));
    }

    @Test
    void noSignalIsUnknownWithZeroConfidence() {
        ClassMetadata plain = analyze("package com.demo; public class Thing {}");
        assertThat(plain.getComponentType()).isEqualTo(ComponentType.UNKNOWN);
        assertThat(plain.getClassification().confidence()).isZero();
    }

    @Test
    void mainMethodMarksTheApplicationEntryPoint() {
        ClassMetadata main = analyze("public class Main { public static void main(String[] args) {} }");
        assertThat(main.getComponentType()).isEqualTo(ComponentType.APPLICATION);
    }

    @Test
    void namingAndPackageConventionsAreWeakerThanAnnotations() {
        ClassMetadata byName = analyze("package com.demo.misc; public class OrderRepository {}");
        ClassMetadata byPackage = analyze("package com.demo.dto; public class Order {}");
        assertThat(byName.getComponentType()).isEqualTo(ComponentType.REPOSITORY);
        assertThat(byPackage.getComponentType()).isEqualTo(ComponentType.DTO);
        assertThat(byPackage.getClassification().confidence()).isLessThan(byName.getClassification().confidence());
    }

    @Test
    void analyzerDescribesEveryTypeInAFile() {
        List<ClassMetadata> types = analyzer.analyzeAll(parse("""
                package com.demo.service;
                import static java.util.Objects.requireNonNull;
                @RestController @RequestMapping("/api/orders")
                public class OrderService {
                    public enum Status { NEW }
                    public record Summary(long id) { }
                    @GetMapping("/{id}") public Summary get() { return null; }
                    Object load() throws Exception { return Class.forName("com.demo.plugin.Plugin"); }
                }
                class Helper { }
                """), null, "OrderService.java", SourceScope.MAIN);

        assertThat(types).extracting(ClassMetadata::getQualifiedName).containsExactly(
                "com.demo.service.OrderService", "com.demo.service.OrderService.Status",
                "com.demo.service.OrderService.Summary", "com.demo.service.Helper");
        ClassMetadata order = types.getFirst();
        assertThat(order.getEndpointPaths()).containsExactly("/api/orders", "/{id}");
        assertThat(order.getStaticImports()).containsExactly("java.util.Objects.requireNonNull");
        assertThat(order.getRiskFlags()).contains(RiskFlag.REFLECTION);
        assertThat(order.getQualifiedStringLiterals()).contains("com.demo.plugin.Plugin");
        assertThat(order.isHasPackagePrivateMembers()).isTrue();
        assertThat(types.get(1).getKind()).isEqualTo(TypeKind.ENUM);
        assertThat(types.get(2).getKind()).isEqualTo(TypeKind.RECORD);
        assertThat(types.get(1).getTopLevelQualifiedName()).isEqualTo("com.demo.service.OrderService");
        assertThat(types.get(3).isPublicType()).isFalse();
    }
}
