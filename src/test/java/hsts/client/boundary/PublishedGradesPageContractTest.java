package hsts.client.boundary;

import hsts.common.PublishedGradeSummaryDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PublishedGradesPageContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/hsts/client/boundary/PublishedGradesPage.java"
    );
    private static final Path FXML = Path.of(
            "src/main/resources/hsts/client/boundary/published-grades-page.fxml"
    );
    private static final Path DASHBOARD_SOURCE = Path.of(
            "src/main/java/hsts/client/boundary/StudentDashboard.java"
    );
    private static final Path DASHBOARD_FXML = Path.of(
            "src/main/resources/hsts/client/boundary/student-dashboard.fxml"
    );
    private static final Path MAIN_CLIENT = Path.of(
            "src/main/java/hsts/client/MainClient.java"
    );

    @Test
    public void realFxmlLoadsAndLaysOutInFreshJavaFxProcess() throws Exception {
        String javaExecutable = Path.of(
                System.getProperty("java.home"), "bin", "java.exe"
        ).toString();
        Process process = new ProcessBuilder(
                javaExecutable, "-cp", System.getProperty("java.class.path"),
                LoadingHarness.class.getName()
        ).redirectErrorStream(true).start();

        assertTrue("JavaFX loading harness timed out",
                process.waitFor(30, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(output, 0, process.exitValue());
    }

    @Test
    public void configureUsesSharedClientControllerWithoutNetworkingLifecycle()
            throws Exception {
        Method configure = PublishedGradesPage.class.getMethod(
                "configure",
                javafx.stage.Stage.class,
                hsts.client.net.Client.class,
                hsts.common.LoginResult.class,
                Runnable.class
        );
        assertNotNull(configure);

        String source = Files.readString(SOURCE);
        assertTrue(source.contains("new ExamExecutionClientController(client)"));
        assertFalse(source.contains("new Client("));
        assertFalse(source.contains("client.sendRequest"));
        assertFalse(source.contains("client.close("));
        assertFalse(source.contains("client.connect("));
        assertFalse(source.contains("client.disconnect("));
        assertFalse(source.contains("RequestType.LOGOUT"));
    }

    @Test
    public void initialDetailAndRefreshOperationsUseAsyncControllerApis()
            throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("getMyPublishedGrades()"));
        assertTrue(source.contains("getMyPublishedGrade(submissionId)"));
        assertTrue(source.contains("getMyPublishedExamReview(submissionId)"));
        assertTrue(source.contains(".whenComplete("));
        assertTrue(source.contains("Platform.runLater("));
        assertFalse(source.contains(".join("));
        assertFalse(source.contains(".get();"));
        assertFalse(source.contains("getUserId()"));
        assertTrue(source.contains("listRequestGeneration++"));
        assertTrue(source.contains("detailRequestGeneration++"));
        assertTrue(source.contains("reviewRequestGeneration++"));
    }

    @Test
    public void orderingEmptyStateAndDisplayHelpersAreSafe() {
        PublishedGradeSummaryDTO third = summary(30);
        PublishedGradeSummaryDTO first = summary(10);
        PublishedGradeSummaryDTO second = summary(20);
        List<PublishedGradeSummaryDTO> ordered =
                PublishedGradesPage.preserveServerOrder(List.of(third, first, second));

        assertSame(third, ordered.get(0));
        assertSame(first, ordered.get(1));
        assertSame(second, ordered.get(2));
        assertThrows(UnsupportedOperationException.class,
                () -> ordered.add(summary(40)));
        assertTrue(PublishedGradesPage.preserveServerOrder(List.of()).isEmpty());
        assertEquals("", PublishedGradesPage.formatDateTime(null));
        assertEquals("No feedback provided", PublishedGradesPage.friendlyFeedback(" "));
        assertEquals("Helpful work", PublishedGradesPage.friendlyFeedback("Helpful work"));
        assertEquals("0.00", PublishedGradesPage.formatDecimal(new BigDecimal("0.00")));
    }

    @Test
    public void staleResponseAndBackGuardsCoverListAndDetailRequests()
            throws Exception {
        assertFalse(PublishedGradesPage.isStale(false, 4, 4));
        assertTrue(PublishedGradesPage.isStale(false, 3, 4));
        assertTrue(PublishedGradesPage.isStale(true, 4, 4));

        String source = Files.readString(SOURCE);
        assertTrue(source.contains("disposed = true;"));
        assertTrue(source.contains("isStale(disposed, generation, listRequestGeneration)"));
        assertTrue(source.contains("isStale(disposed, generation, detailRequestGeneration)"));
        assertTrue(source.contains("selectedSubmissionId() != submissionId"));
        assertFalse(source.contains("navigation.run();\n            client.close"));
    }

    @Test
    public void serverErrorsArePreservedAndBlankLocalFailuresUseSafeFallback() {
        assertEquals(
                "Published grade is unavailable",
                PublishedGradesPage.cleanError(
                        new CompletionException(new IllegalStateException(
                                "Published grade is unavailable"
                        )),
                        "Unable to load published grade"
                )
        );
        assertEquals(
                "Unable to load published grade",
                PublishedGradesPage.cleanError(
                        new IllegalStateException(" "),
                        "Unable to load published grade"
                )
        );
    }

    @Test
    public void pageUsesOnlyStudentPublishedContractsAndNoPrivateManagerData()
            throws Exception {
        String content = (Files.readString(SOURCE) + Files.readString(FXML))
                .toLowerCase();
        assertTrue(content.contains("publishedgradesummarydto"));
        assertTrue(content.contains("publishedgradedto"));
        assertTrue(content.contains("publishedexamreviewdto"));
        assertTrue(content.contains("publishedexamquestionreviewdto"));
        for (String forbidden : List.of(
                "executionsubmissionsummarydto", "submissionreviewdto",
                "submissionanswerreviewdto", "automaticscore",
                "adjustmentreason", "revieweruserid", "publisheruserid",
                "studentanswer", "examsubmission", "hsts.server.entity",
                "hsts.server.repository", "hsts.server.control"
        )) {
            assertFalse("Forbidden published-grade content: " + forbidden,
                    content.contains(forbidden));
        }
    }

    @Test
    public void fxmlIsWellFormedUniqueAndMatchesController() throws Exception {
        Document document = parse(FXML);
        assertEquals(
                "hsts.client.boundary.PublishedGradesPage",
                document.getDocumentElement().getAttribute("fx:controller")
        );

        Map<String, Element> ids = new HashMap<>();
        collectIds(document.getDocumentElement(), ids);
        for (String required : List.of(
                "gradeTable", "examTitleColumn", "courseColumn",
                "finalScoreColumn", "submittedColumn", "publishedColumn",
                "detailExamTitleValue", "detailCourseValue",
                "detailFinalScoreValue", "feedbackArea", "submittedValue",
                "reviewedValue", "publishedValue", "refreshButton",
                "backButton", "statusLabel", "busyIndicator"
                , "reviewStatusLabel", "reviewExamValue", "reviewCourseValue",
                "reviewExecutionValue", "reviewVersionValue",
                "reviewFinalScoreValue", "reviewSubmittedValue",
                "reviewReviewedValue", "reviewPublishedValue",
                "reviewFeedbackArea", "questionPositionLabel",
                "questionOutcomeLabel", "questionMetadataLabel",
                "questionScoreLabel", "reviewQuestionContentArea",
                "answerOptionsBox", "previousQuestionButton", "nextQuestionButton"
        )) {
            assertTrue("Missing fx:id " + required, ids.containsKey(required));
        }
        for (Map.Entry<String, Element> entry : ids.entrySet()) {
            Field field = PublishedGradesPage.class.getDeclaredField(entry.getKey());
            assertTrue(field.isAnnotationPresent(FXML.class));
            assertTrue(
                    "Incompatible field " + entry.getKey(),
                    field.getType().isAssignableFrom(
                            fxmlType(entry.getValue().getTagName())
                    )
            );
        }

        Set<String> actions = new HashSet<>();
        collectActions(document.getDocumentElement(), actions);
        assertEquals(Set.of(
                "handleRefresh", "handleBack", "handlePreviousQuestion",
                "handleNextQuestion"
        ), actions);
        for (String action : actions) {
            Method method = PublishedGradesPage.class.getDeclaredMethod(action);
            assertTrue(method.isAnnotationPresent(FXML.class));
        }
    }

    @Test
    public void publishedGradesFxmlResolvesAtRuntimeAndUsesNodePlaceholder()
            throws Exception {
        URL resource = PublishedGradesPage.class.getResource(
                "/hsts/client/boundary/published-grades-page.fxml"
        );
        assertNotNull(resource);
        FXMLLoader loader = new FXMLLoader(resource);
        assertEquals(resource, loader.getLocation());

        Document document = parse(FXML);
        Element gradeTable = findByFxId(document.getDocumentElement(), "gradeTable");
        assertNotNull(gradeTable);
        assertFalse(gradeTable.hasAttribute("placeholder"));
        NodeList placeholders = gradeTable.getElementsByTagName("placeholder");
        assertEquals(1, placeholders.getLength());
        assertEquals(1, ((Element) placeholders.item(0))
                .getElementsByTagName("Label").getLength());
        assertNotNull(javafx.scene.control.TableView.class.getMethod(
                "setPlaceholder", javafx.scene.Node.class
        ));
    }

    @Test
    public void dashboardAndMainClientWireStudentSharedClientNavigation()
            throws Exception {
        String dashboard = Files.readString(DASHBOARD_SOURCE);
        String dashboardFxml = Files.readString(DASHBOARD_FXML);
        String mainClient = Files.readString(MAIN_CLIENT);

        assertTrue(dashboard.contains("publishedGradesHandler"));
        assertTrue(dashboard.contains("handlePublishedGrades"));
        assertTrue(dashboard.contains("loginResult.getRole() != UserRole.STUDENT"));
        assertTrue(dashboard.contains("publishedGradesHandler.run()"));
        assertTrue(dashboardFxml.contains("fx:id=\"publishedGradesButton\""));
        assertTrue(dashboardFxml.contains("onAction=\"#handlePublishedGrades\""));
        assertFalse(dashboardFxml.contains("Available Exams"));
        assertEquals(1, occurrences(dashboardFxml, "publishedGradesButton"));
        assertTrue(dashboardFxml.contains("fx:id=\"examExecutionButton\""));
        assertTrue(dashboardFxml.contains("onAction=\"#handleLogout\""));

        String navigation = methodSource(
                mainClient,
                "private void showPublishedGrades("
        );
        assertTrue(navigation.contains(
                "/hsts/client/boundary/published-grades-page.fxml"
        ));
        assertTrue(navigation.contains("stage"));
        assertTrue(navigation.contains("client"));
        assertTrue(navigation.contains("loginResult"));
        assertTrue(navigation.contains("returnToStudentDashboard(loginResult)"));
        assertFalse(navigation.contains("new Client("));
        assertFalse(navigation.contains("closeQuietly("));
        assertFalse(navigation.contains("logout("));
    }

    private static PublishedGradeSummaryDTO summary(int submissionId) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        return new PublishedGradeSummaryDTO(
                submissionId, 80, 40, 3, "Exam " + submissionId,
                "Mathematics", new BigDecimal("88.50"),
                now.minusHours(1), now
        );
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static void collectIds(Element element, Map<String, Element> ids) {
        String id = element.getAttribute("fx:id");
        if (!id.isEmpty()) {
            assertFalse("Duplicate fx:id " + id, ids.containsKey(id));
            ids.put(id, element);
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                collectIds(childElement, ids);
            }
        }
    }

    private static void collectActions(Element element, Set<String> actions) {
        String action = element.getAttribute("onAction");
        if (action.startsWith("#")) {
            actions.add(action.substring(1));
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                collectActions(childElement, actions);
            }
        }
    }

    private static Class<?> fxmlType(String tagName) {
        return switch (tagName) {
            case "Label" -> javafx.scene.control.Label.class;
            case "Button" -> javafx.scene.control.Button.class;
            case "TableView" -> javafx.scene.control.TableView.class;
            case "TableColumn" -> javafx.scene.control.TableColumn.class;
            case "TextArea" -> javafx.scene.control.TextArea.class;
            case "ProgressIndicator" -> javafx.scene.control.ProgressIndicator.class;
            case "VBox" -> javafx.scene.layout.VBox.class;
            case "ImageView" -> javafx.scene.image.ImageView.class;
            default -> throw new AssertionError(
                    "Unexpected fx:id element " + tagName
            );
        };
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static Element findByFxId(Element element, String fxId) {
        if (fxId.equals(element.getAttribute("fx:id"))) {
            return element;
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                Element result = findByFxId(childElement, fxId);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static String methodSource(String source, String methodStart) {
        int start = source.indexOf(methodStart);
        int openingBrace = source.indexOf('{', start);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Method not found: " + methodStart);
    }

    public static final class LoadingHarness {
        private LoadingHarness() {
        }

        public static void main(String[] args) {
            Platform.startup(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            PublishedGradesPageContractTest.class.getResource(
                                    "/hsts/client/boundary/published-grades-page.fxml"
                            )
                    );
                    Parent root = loader.load();
                    new Scene(root, 1180, 760);
                    root.applyCss();
                    root.layout();
                    if (!(loader.getController() instanceof PublishedGradesPage)) {
                        throw new AssertionError("Unexpected FXML controller");
                    }
                    System.exit(0);
                } catch (Throwable failure) {
                    failure.printStackTrace(System.err);
                    System.exit(1);
                }
            });
        }
    }
}
