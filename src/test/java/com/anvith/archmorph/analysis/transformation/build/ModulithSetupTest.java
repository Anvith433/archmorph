package com.anvith.archmorph.analysis.transformation.build;

import com.anvith.archmorph.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithSetupTest {

    @TempDir
    Path temp;

    @Test
    void mapsSpringBootLinesToSpringModulithReleases() {
        assertThat(ModulithSetup.modulithVersionFor("4.1.0")).contains("2.1.1");
        assertThat(ModulithSetup.modulithVersionFor("4.0.3")).contains("2.0.8");
        assertThat(ModulithSetup.modulithVersionFor("3.5.6")).contains("1.4.13");
        assertThat(ModulithSetup.modulithVersionFor("2.7.18")).isEmpty();
        assertThat(ModulithSetup.modulithVersionFor(null)).isEmpty();
    }

    @Test
    void readsTheSpringBootVersionFromParentImportOrProperty() {
        assertThat(ModulithSetup.springBootVersion(PomDocument.parse("""
                <project><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId>
                <version>4.1.0</version></parent></project>"""))).contains("4.1.0");
        assertThat(ModulithSetup.springBootVersion(PomDocument.parse("""
                <project><properties><boot.version>3.5.1</boot.version></properties>
                <dependencyManagement><dependencies><dependency><groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId><version>${boot.version}</version><type>pom</type>
                <scope>import</scope></dependency></dependencies></dependencyManagement></project>"""))).contains("3.5.1");
        assertThat(ModulithSetup.springBootVersion(PomDocument.parse("<project><!-- <parent><version>9</version></parent> --></project>")))
                .isEmpty();
    }

    @Test
    void addsBomAndTestDependencyWithoutReformattingThePom() throws Exception {
        String original = Files.readString(Fixtures.path("spring-layered").resolve("pom.xml"));
        String updated = ModulithSetup.addDependencies(PomDocument.parse(original), "2.1.1");

        Document xml = parse(updated);
        assertThat(xpath(xml, "/project/dependencyManagement/dependencies/dependency[artifactId='spring-modulith-bom']/version"))
                .isEqualTo("2.1.1");
        assertThat(xpath(xml, "/project/dependencyManagement/dependencies/dependency[artifactId='spring-modulith-bom']/scope"))
                .isEqualTo("import");
        assertThat(xpath(xml, "/project/dependencies/dependency[artifactId='spring-modulith-starter-test']/scope")).isEqualTo("test");
        // every original line is still there, unchanged and in order
        assertThat(isSubsequence(original.lines().toList(), updated.lines().toList())).isTrue();
        assertThat(updated).contains("    <dependencyManagement>\n        <dependencies>\n            <dependency>\n");
    }

    @Test
    void extendsAnExistingDependencyManagementAndKeepsCrlf() throws Exception {
        String original = String.join("\r\n",
                "<project>",
                "  <modelVersion>4.0.0</modelVersion>",
                "  <dependencyManagement>",
                "    <dependencies>",
                "      <!-- a comment with <dependencies> inside -->",
                "    </dependencies>",
                "  </dependencyManagement>",
                "  <dependencies>",
                "    <dependency><groupId>a</groupId><artifactId>b</artifactId></dependency>",
                "  </dependencies>",
                "</project>", "");
        String updated = ModulithSetup.addDependencies(PomDocument.parse(original), "2.1.1");
        Document xml = parse(updated);
        assertThat(xpath(xml, "count(/project/dependencyManagement/dependencies/dependency)")).isEqualTo("1");
        assertThat(xpath(xml, "count(/project/dependencies/dependency)")).isEqualTo("2");
        assertThat(updated.replace("\r\n", "")).doesNotContain("\n");
        assertThat(updated).contains("  <dependencies>\r\n    <dependency><groupId>a</groupId>");
    }

    @Test
    void writesTheModularityTestNextToTheApplicationClass() throws Exception {
        Path project = Fixtures.copyTo("spring-layered", temp.resolve("p"));
        ModulithSetup.Result result = new ModulithSetup().apply(project, "com.demo.DemoApplication", "");

        assertThat(result.pomUpdated()).isTrue();
        assertThat(result.modulithVersion()).isEqualTo("2.1.1");
        assertThat(result.testFile()).isEqualTo("src/test/java/com/demo/ModularityTests.java");
        assertThat(Files.readString(project.resolve(result.testFile())))
                .contains("package com.demo;", "ApplicationModules.of(DemoApplication.class).verify();");

        // running it again changes nothing in the pom and does not overwrite the test
        String pom = Files.readString(project.resolve("pom.xml"));
        ModulithSetup.Result again = new ModulithSetup().apply(project, "com.demo.DemoApplication", "");
        assertThat(Files.readString(project.resolve("pom.xml"))).isEqualTo(pom);
        assertThat(again.testFile()).isEqualTo("src/test/java/com/demo/ArchMorphModularityTests.java");
        assertThat(again.warnings()).anyMatch(w -> w.contains("already mentions Spring Modulith"));
    }

    private static boolean isSubsequence(java.util.List<String> needle, java.util.List<String> haystack) {
        int i = 0;
        for (String line : haystack) {
            if (i < needle.size() && needle.get(i).equals(line)) {
                i++;
            }
        }
        return i == needle.size();
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        // XPath name tests below ignore namespaces; drop the POM default namespace declaration for them
        String plain = xml.replaceFirst("\\sxmlns=\"[^\"]*\"", "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private static String xpath(Document xml, String expression) throws Exception {
        String raw = (String) XPathFactory.newInstance().newXPath().evaluate(expression, xml, XPathConstants.STRING);
        return raw.trim();
    }
}
