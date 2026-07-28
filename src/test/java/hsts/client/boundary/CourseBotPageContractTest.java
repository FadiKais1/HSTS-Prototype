package hsts.client.boundary;

import hsts.common.type.BotSourceType;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CourseBotPageContractTest {
    private static final Path MANAGEMENT = Path.of(
            "src/main/java/hsts/client/boundary/CourseBotManagementPage.java");
    private static final Path STUDENT = Path.of(
            "src/main/java/hsts/client/boundary/CourseBotPage.java");
    private static final Path MAIN = Path.of("src/main/java/hsts/client/MainClient.java");

    @Test
    public void supportedFileTypesAreExactAndCaseInsensitive() {
        assertEquals(BotSourceType.TXT, CourseBotManagementPage.sourceType("notes.TXT"));
        assertEquals(BotSourceType.PDF, CourseBotManagementPage.sourceType("lecture.pdf"));
        assertEquals(BotSourceType.DOCX, CourseBotManagementPage.sourceType("guide.DoCx"));
        assertEquals(null, CourseBotManagementPage.sourceType("archive.zip"));
        assertEquals(null, CourseBotManagementPage.sourceType("notes.txt.exe"));
    }

    @Test
    public void boundariesUseControllersWithoutDirectNetworkingOrServerTypes() throws Exception {
        for (Path path : List.of(MANAGEMENT, STUDENT)) {
            String source = Files.readString(path);
            assertTrue(source.contains("CourseBotClientController"));
            for (String forbidden : List.of(
                    "client.sendRequest", "new Client(", "RequestType", "hsts.common.Request",
                    "hsts.common.Response", "hsts.server.", "repository", "ExternalBotSystem",
                    ".join(", "Future.get(", "client.close(", "client.disconnect(")) {
                assertFalse(path + " contains " + forbidden, source.contains(forbidden));
            }
        }
        String manager = Files.readString(MANAGEMENT);
        assertTrue(manager.contains("questionController.getMyCourses()"));
        assertTrue(manager.contains("questionController.listQuestions(filter)"));
        assertTrue(manager.contains("question.getQuestionId(), question.getVersionNo()"));
        assertFalse(manager.contains("getCorrectOptionNumber"));
        assertTrue(manager.contains("CompletableFuture.supplyAsync(() -> readFile(selected))"));
        assertTrue(manager.contains("selected.toPath().getFileName().toString()"));
    }

    @Test
    public void studentHistoryUsesOnlyPersonalContractsAndNeutralNoAnswerText() throws Exception {
        String source = Files.readString(STUDENT);
        assertTrue(source.contains("No suitable answer was found in the course material"));
        assertTrue(source.contains("Course Bot is unavailable during an active exam"));
        assertTrue(source.contains("history.getMessages()"));
        assertFalse(source.contains("conversationId"));
        assertFalse(source.contains("provider"));
        assertFalse(source.contains("studentId"));
    }

    @Test
    public void navigationReusesSharedClientAndEnforcesRoles() throws Exception {
        String source = Files.readString(MAIN);
        int manager = source.indexOf("private void showCourseBotManagement(");
        int student = source.indexOf("private void showCourseBot(");
        assertTrue(manager >= 0 && student > manager);
        String navigation = source.substring(manager, source.indexOf("private void showPrincipalDashboard("));
        assertTrue(navigation.contains("/hsts/client/boundary/course-bot-management-page.fxml"));
        assertTrue(navigation.contains("/hsts/client/boundary/course-bot-page.fxml"));
        assertTrue(navigation.contains("controller.configure(stage, client, loginResult"));
        assertFalse(navigation.contains("new Client("));
        assertFalse(navigation.contains(".close("));
        assertTrue(navigation.contains("UserRole.STUDENT"));
        assertTrue(navigation.contains("UserRole.TEACHER"));
        assertTrue(navigation.contains("UserRole.COORDINATOR"));
    }

    @Test
    public void fxmlContractsAreWellFormedUniqueAndCompatible() throws Exception {
        verifyFxml("course-bot-management-page.fxml", CourseBotManagementPage.class);
        verifyFxml("course-bot-page.fxml", CourseBotPage.class);
    }

    @Test
    public void realFxmlLoadsAndLaysOutInFreshJavaFxProcess() throws Exception {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", System.getProperty("java.class.path"), RenderingHarness.class.getName())
                .redirectErrorStream(true).start();
        assertTrue("JavaFX harness timed out", process.waitFor(30, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(output, 0, process.exitValue());
    }

    private static void verifyFxml(String name, Class<?> controller) throws Exception {
        Path path = Path.of("src/main/resources/hsts/client/boundary", name);
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(path.toFile());
        assertEquals(controller.getName(),
                document.getDocumentElement().getAttribute("fx:controller"));
        Set<String> ids = new HashSet<>();
        Set<String> handlers = new HashSet<>();
        collect(document.getDocumentElement(), ids, handlers);
        for (String id : ids) {
            Field field = controller.getDeclaredField(id);
            assertNotNull(field);
        }
        for (String handler : handlers) {
            Method method = controller.getDeclaredMethod(handler);
            assertTrue(method.isAnnotationPresent(javafx.fxml.FXML.class));
        }
        String fxml = Files.readString(path);
        for (String forbidden : List.of("new Client", "RequestType", "repository",
                "ExternalBotSystem", "correctOption", "studentId", "file://")) {
            assertFalse(fxml.contains(forbidden));
        }
    }

    private static void collect(Element element, Set<String> ids, Set<String> handlers) {
        String id = element.getAttribute("fx:id");
        if (!id.isBlank()) assertTrue("Duplicate fx:id " + id, ids.add(id));
        String action = element.getAttribute("onAction");
        if (action.startsWith("#")) handlers.add(action.substring(1));
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) collect(childElement, ids, handlers);
        }
    }

    public static final class RenderingHarness {
        private RenderingHarness() { }
        public static void main(String[] args) {
            Platform.startup(() -> {
                try {
                    load("/hsts/client/boundary/course-bot-management-page.fxml");
                    load("/hsts/client/boundary/course-bot-page.fxml");
                    System.exit(0);
                } catch (Throwable failure) {
                    failure.printStackTrace(System.err);
                    System.exit(1);
                }
            });
        }
        private static void load(String resource) throws Exception {
            FXMLLoader loader = new FXMLLoader(CourseBotPage.class.getResource(resource));
            Parent root = loader.load();
            new Scene(root);
            root.applyCss();
            root.layout();
        }
    }
}
