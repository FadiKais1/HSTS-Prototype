package hsts.packaging;

import hsts.client.ClientLauncher;
import javafx.application.Application;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PackagingConfigurationTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    @Test
    public void definesDistinctRequiredArtifactNames() throws Exception {
        Element client = shadeExecution("package-client");
        Element server = shadeExecution("package-server");

        String clientOutput = descendantText(client, "outputFile");
        String serverOutput = descendantText(server, "outputFile");

        assertTrue(clientOutput.endsWith("/G7_Client.jar"));
        assertTrue(serverOutput.endsWith("/G7_Server.jar"));
        assertNotEquals(clientOutput, serverOutput);
    }

    @Test
    public void assignsTheCorrectMainClassToEachArtifact() throws Exception {
        assertEquals(
                "hsts.client.ClientLauncher",
                descendantText(shadeExecution("package-client"), "mainClass")
        );
        assertEquals(
                "hsts.server.MainServer",
                descendantText(shadeExecution("package-server"), "mainClass")
        );
    }

    @Test
    public void mergesServicesAndFiltersInvalidSignatures() throws Exception {
        for (String executionId : List.of("package-client", "package-server")) {
            Element execution = shadeExecution(executionId);
            List<String> implementations = descendantAttributes(
                    execution,
                    "transformer",
                    "implementation"
            );
            assertTrue(implementations.contains(
                    "org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"
            ));

            List<String> excludes = descendantTexts(execution, "exclude");
            assertTrue(excludes.contains("META-INF/*.SF"));
            assertTrue(excludes.contains("META-INF/*.RSA"));
            assertTrue(excludes.contains("META-INF/*.DSA"));
            assertEquals("false", descendantText(execution, "createDependencyReducedPom"));
        }
    }

    @Test
    public void clientLauncherIsAnOrdinaryMainClass() throws Exception {
        assertFalse(Application.class.isAssignableFrom(ClientLauncher.class));
        Method main = ClientLauncher.class.getMethod("main", String[].class);
        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }

    @Test
    public void generatedArtifactsRemainIgnoredByGit() throws Exception {
        List<String> ignoreLines = Files.readAllLines(PROJECT_ROOT.resolve(".gitignore"));
        assertTrue(ignoreLines.stream().map(String::trim).anyMatch("target/"::equals));
    }

    @Test
    public void definesServerOnlyPdfAndDocxDependencies() throws Exception {
        assertEquals("3.0.5", dependencyVersion("org.apache.pdfbox", "pdfbox"));
        assertEquals("5.4.1", dependencyVersion("org.apache.poi", "poi-ooxml"));
        assertEquals("2.24.3", dependencyVersion(
                "org.apache.logging.log4j", "log4j-to-slf4j"
        ));
        assertEquals("2.19.2", dependencyVersion(
                "com.fasterxml.jackson.core", "jackson-databind"
        ));

        List<String> clientExcludes = descendantTexts(
                shadeExecution("package-client"), "exclude"
        );
        for (String excluded : List.of(
                "org.apache.pdfbox:*",
                "org.apache.poi:*",
                "org.apache.xmlbeans:xmlbeans",
                "org.apache.commons:commons-compress",
                "org.apache.commons:commons-collections4",
                "org.apache.commons:commons-lang3",
                "commons-io:commons-io",
                "commons-codec:commons-codec",
                "commons-logging:commons-logging",
                "com.github.virtuald:curvesapi",
                "com.zaxxer:SparseBitSet",
                "org.apache.logging.log4j:*"
                , "com.fasterxml.jackson.core:*"
        )) {
            assertTrue("Client does not exclude " + excluded,
                    clientExcludes.contains(excluded));
        }

        List<String> clientProjectExcludes = projectClassExcludes(
                shadeExecution("package-client")
        );
        assertTrue(clientProjectExcludes.contains("hsts/server/bot/**"));
        assertTrue(clientProjectExcludes.contains("hsts/external/**"));
    }

    private static List<String> projectClassExcludes(Element execution) {
        NodeList filters = execution.getElementsByTagName("filter");
        for (int index = 0; index < filters.getLength(); index++) {
            Element filter = (Element) filters.item(index);
            if ("hsts:hsts-prototype".equals(descendantText(filter, "artifact"))) {
                return descendantTexts(filter, "exclude");
            }
        }
        throw new AssertionError("Missing client project-class filter");
    }

    private static Element shadeExecution(String executionId) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(PROJECT_ROOT.resolve("pom.xml").toFile());
        NodeList executions = document.getElementsByTagName("execution");
        for (int index = 0; index < executions.getLength(); index++) {
            Element execution = (Element) executions.item(index);
            if (executionId.equals(childText(execution, "id"))) {
                return execution;
            }
        }
        throw new AssertionError("Missing Maven execution: " + executionId);
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        throw new AssertionError("Missing child element: " + name);
    }

    private static String descendantText(Element parent, String name) {
        NodeList descendants = parent.getElementsByTagName(name);
        if (descendants.getLength() == 0) {
            throw new AssertionError("Missing descendant element: " + name);
        }
        return descendants.item(0).getTextContent().trim();
    }

    private static List<String> descendantTexts(Element parent, String name) {
        NodeList descendants = parent.getElementsByTagName(name);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < descendants.getLength(); index++) {
            values.add(descendants.item(index).getTextContent().trim());
        }
        return values;
    }

    private static List<String> descendantAttributes(
            Element parent,
            String name,
            String attribute
    ) {
        NodeList descendants = parent.getElementsByTagName(name);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < descendants.getLength(); index++) {
            values.add(((Element) descendants.item(index)).getAttribute(attribute));
        }
        return values;
    }

    private static String dependencyVersion(String groupId, String artifactId)
            throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(PROJECT_ROOT.resolve("pom.xml").toFile());
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            if (groupId.equals(childText(dependency, "groupId"))
                    && artifactId.equals(childText(dependency, "artifactId"))) {
                return childText(dependency, "version");
            }
        }
        throw new AssertionError("Missing dependency: " + groupId + ":" + artifactId);
    }
}
