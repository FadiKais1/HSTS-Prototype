package hsts.client.boundary;

import hsts.common.SubmissionReviewDTO;
import hsts.common.type.SubmissionStatus;
import javafx.fxml.FXML;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GradeReviewPageContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/hsts/client/boundary/GradeReviewPage.java"
    );
    private static final Path FXML = Path.of(
            "src/main/resources/hsts/client/boundary/grade-review-page.fxml"
    );
    private static final Path DASHBOARD_SOURCE = Path.of(
            "src/main/java/hsts/client/boundary/TeacherDashboard.java"
    );
    private static final Path DASHBOARD_FXML = Path.of(
            "src/main/resources/hsts/client/boundary/teacher-dashboard.fxml"
    );
    private static final Path MAIN_CLIENT = Path.of(
            "src/main/java/hsts/client/MainClient.java"
    );
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 15, 12, 0);

    @Test
    public void configureUsesSharedClientControllerAndNoDirectNetworkingLifecycle()
            throws Exception {
        Method configure = GradeReviewPage.class.getMethod(
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
        assertFalse(source.contains("hsts.server.repository"));
        assertFalse(source.contains("hsts.server.control"));
        assertFalse(source.contains("hsts.server.entity"));
    }

    @Test
    public void asyncOperationsUseExistingControllerAndAuthoritativeTimestamp()
            throws Exception {
        String source = Files.readString(SOURCE);
        for (String call : List.of(
                "getMyExamExecutions()",
                "getExecutionSubmissions(executionId)",
                "getSubmissionForReview(submissionId)",
                "reviewSubmissionGrade(payload)",
                "publishSubmissionGrade(payload)"
        )) {
            assertTrue("Missing controller call " + call, source.contains(call));
        }
        assertTrue(source.contains("review.getUpdatedAt()"));
        assertFalse(source.contains("new ReviewSubmissionPayload(\n"
                + "                review.getSubmissionId(),\n"
                + "                finalScore,\n"
                + "                feedbackArea.getText(),\n"
                + "                adjustmentReasonArea.getText(),\n"
                + "                review.getReviewedAt()"));
        assertFalse(source.contains("new PublishSubmissionPayload(\n"
                + "                review.getSubmissionId(),\n"
                + "                review.getPublishedAt()"));
        assertFalse(source.contains(".join("));
        assertTrue(source.contains(".whenComplete("));
        assertTrue(source.contains("Platform.runLater("));
    }

    @Test
    public void finalScoreValidationUsesBigDecimalWithoutDoubleConversion() {
        assertEquals(new BigDecimal("65.25"), GradeReviewPage.parseFinalScore(" 65.25 "));
        assertEquals(new BigDecimal("65"), GradeReviewPage.parseFinalScore("65"));
        assertValidationError(null, "Final score is required");
        assertValidationError(" ", "Final score is required");
        assertValidationError("abc", "Final score must be a valid decimal");
        assertValidationError("-0.01", "Final score must be between 0.00 and 100.00");
        assertValidationError("100.01", "Final score must be between 0.00 and 100.00");
        assertValidationError(
                "10.001",
                "Final score must have no more than two decimal places"
        );
    }

    @Test
    public void changedScoreRequiresReasonButEqualScoreDoesNot() {
        GradeReviewPage.requireAdjustmentReason(
                new BigDecimal("80.00"), new BigDecimal("80.0"), ""
        );
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> GradeReviewPage.requireAdjustmentReason(
                        new BigDecimal("80.00"), new BigDecimal("81.00"), " "
                )
        );
        assertEquals(
                "Adjustment reason is required when changing the automatic score",
                missing.getMessage()
        );
        GradeReviewPage.requireAdjustmentReason(
                new BigDecimal("80.00"), new BigDecimal("81.00"), "Accepted work"
        );
    }

    @Test
    public void typedLifecycleSeparatesReviewAndPublication() {
        SubmissionReviewDTO unreviewed = review(
                SubmissionStatus.SUBMITTED, null, null, 0
        );
        SubmissionReviewDTO reviewed = review(
                SubmissionStatus.AUTO_SUBMITTED, NOW, null, 0
        );
        SubmissionReviewDTO published = review(
                SubmissionStatus.PUBLISHED, NOW, NOW.plusMinutes(1), 1003
        );
        SubmissionReviewDTO inProgress = review(
                SubmissionStatus.IN_PROGRESS, null, null, 0
        );

        assertTrue(GradeReviewPage.canReview(unreviewed));
        assertFalse(GradeReviewPage.canPublish(unreviewed));
        assertFalse(GradeReviewPage.canReview(reviewed));
        assertTrue(GradeReviewPage.canPublish(reviewed));
        assertFalse(GradeReviewPage.canReview(published));
        assertFalse(GradeReviewPage.canPublish(published));
        assertFalse(GradeReviewPage.canReview(inProgress));
        assertFalse(GradeReviewPage.canPublish(inProgress));
    }

    @Test
    public void staleAndDuplicateMutationGuardsArePresent() throws Exception {
        String source = Files.readString(SOURCE);
        for (String token : List.of(
                "disposed", "executionRequestGeneration",
                "submissionRequestGeneration", "detailRequestGeneration",
                "mutationRequestGeneration", "mutationInProgress",
                "isStale(disposed"
        )) {
            assertTrue("Missing guard " + token, source.contains(token));
        }
        assertTrue(GradeReviewPage.isStale(true, 1, 1));
        assertTrue(GradeReviewPage.isStale(false, 1, 2));
        assertFalse(GradeReviewPage.isStale(false, 2, 2));
        assertTrue(source.contains("disposed = true;"));
        assertFalse(source.contains("backHandler.run();\n            client.close"));

        int saveStart = source.indexOf("private void handleSaveReview()");
        int publishStart = source.indexOf("private void handlePublishGrade()");
        String saveMethod = source.substring(saveStart, publishStart);
        assertFalse(saveMethod.contains("publishSubmissionGrade"));
    }

    @Test
    public void fxmlIsWellFormedUniqueAndMatchesController() throws Exception {
        Document document = parse(FXML);
        assertEquals(
                "hsts.client.boundary.GradeReviewPage",
                document.getDocumentElement().getAttribute("fx:controller")
        );
        Map<String, Element> ids = new HashMap<>();
        collectIds(document.getDocumentElement(), ids);
        for (String required : List.of(
                "executionComboBox", "submissionTable", "answerTable",
                "finalScoreField", "feedbackArea", "adjustmentReasonArea",
                "saveReviewButton", "publishGradeButton", "refreshButton",
                "backButton", "statusLabel", "busyIndicator"
        )) {
            assertTrue("Missing fx:id " + required, ids.containsKey(required));
        }

        for (Map.Entry<String, Element> entry : ids.entrySet()) {
            Field field = GradeReviewPage.class.getDeclaredField(entry.getKey());
            assertTrue(field.isAnnotationPresent(FXML.class));
            assertTrue(
                    "Incompatible field " + entry.getKey(),
                    field.getType().isAssignableFrom(fxmlType(entry.getValue().getTagName()))
            );
        }

        Set<String> actions = new HashSet<>();
        collectActions(document.getDocumentElement(), actions);
        for (String action : actions) {
            Method method = GradeReviewPage.class.getDeclaredMethod(action);
            assertTrue(method.isAnnotationPresent(FXML.class));
        }
        assertEquals(
                Set.of("handleSaveReview", "handlePublishGrade", "handleRefresh", "handleBack"),
                actions
        );

        String fxml = Files.readString(FXML).toLowerCase();
        assertFalse(fxml.contains("correct option"));
        assertFalse(fxml.contains("correct_option"));
        assertFalse(fxml.contains("new client"));
        assertFalse(fxml.contains("sendrequest"));
    }

    @Test
    public void dashboardAndMainClientWireRoleScopedSharedClientNavigation()
            throws Exception {
        String dashboard = Files.readString(DASHBOARD_SOURCE);
        String dashboardFxml = Files.readString(DASHBOARD_FXML);
        String mainClient = Files.readString(MAIN_CLIENT);

        assertTrue(dashboard.contains("gradeReviewHandler"));
        assertTrue(dashboard.contains("handleGradeReview"));
        assertTrue(dashboard.contains("UserRole.TEACHER"));
        assertTrue(dashboard.contains("UserRole.COORDINATOR"));
        assertTrue(dashboard.contains("updateApprovalRequestsState()"));
        assertTrue(dashboardFxml.contains("fx:id=\"gradeReviewButton\""));
        assertTrue(dashboardFxml.contains("onAction=\"#handleGradeReview\""));
        assertTrue(dashboardFxml.contains("fx:id=\"approvalRequestsButton\""));

        assertTrue(mainClient.contains("showGradeReview(loginResult)"));
        assertTrue(mainClient.contains("/hsts/client/boundary/grade-review-page.fxml"));
        assertTrue(mainClient.contains("controller.configure("));
        assertTrue(mainClient.contains("stage,\n                    client,\n                    loginResult"));
        assertTrue(mainClient.contains("returnToTeacherDashboard(loginResult)"));
    }

    private static void assertValidationError(String value, String message) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GradeReviewPage.parseFinalScore(value)
        );
        assertEquals(message, exception.getMessage());
    }

    private static SubmissionReviewDTO review(
            SubmissionStatus status,
            LocalDateTime reviewedAt,
            LocalDateTime publishedAt,
            int publisherId
    ) {
        return new SubmissionReviewDTO(
                501, 81, 40, 3, "Midterm", 1001, "Student", status,
                new BigDecimal("80.00"), new BigDecimal("80.00"),
                "Feedback", "", reviewedAt == null ? 0 : 1002,
                NOW.minusHours(1), NOW.minusMinutes(5), reviewedAt, NOW,
                publisherId, publishedAt, List.of()
        );
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
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
            case "ComboBox" -> javafx.scene.control.ComboBox.class;
            case "TableView" -> javafx.scene.control.TableView.class;
            case "TableColumn" -> javafx.scene.control.TableColumn.class;
            case "TextField" -> javafx.scene.control.TextField.class;
            case "TextArea" -> javafx.scene.control.TextArea.class;
            case "ProgressIndicator" -> javafx.scene.control.ProgressIndicator.class;
            default -> throw new AssertionError("Unexpected fx:id element " + tagName);
        };
    }
}
