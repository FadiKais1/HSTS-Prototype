package hsts.client.boundary;

import hsts.common.ExamQuestionDTO;
import hsts.common.type.ExamStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ApprovalRequestsPageContractTest {
    private static final Path FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/approval-requests-page.fxml"
    );
    private static final Path CONTROLLER_PATH = Path.of(
            "src/main/java/hsts/client/boundary/ApprovalRequestsPage.java"
    );
    private static final Path DASHBOARD_PATH = Path.of(
            "src/main/java/hsts/client/boundary/TeacherDashboard.java"
    );
    private static final Path DASHBOARD_FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/teacher-dashboard.fxml"
    );
    private static final Path MAIN_CLIENT_PATH = Path.of(
            "src/main/java/hsts/client/MainClient.java"
    );

    @Test
    public void fxmlIsWellFormedAndUsesExpectedController() throws Exception {
        Document document = parse(FXML_PATH);

        assertEquals(
                "hsts.client.boundary.ApprovalRequestsPage",
                document.getDocumentElement().getAttribute("fx:controller")
        );
    }

    @Test
    public void everyFxIdAndActionMapsToControllerWithoutDuplicates() throws Exception {
        Document document = parse(FXML_PATH);
        Map<String, String> ids = new LinkedHashMap<>();
        Set<String> actions = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);
        collectActions(document.getDocumentElement(), actions);

        for (Map.Entry<String, String> entry : ids.entrySet()) {
            Field field = ApprovalRequestsPage.class.getDeclaredField(entry.getKey());
            assertEquals(entry.getValue(), field.getType().getSimpleName());
        }
        Set<String> methods = new HashSet<>();
        for (Method method : ApprovalRequestsPage.class.getDeclaredMethods()) {
            methods.add(method.getName());
        }
        for (String action : actions) {
            assertTrue("Missing controller action: " + action, methods.contains(action));
        }
    }

    @Test
    public void requiredTablesDetailsAndActionsArePresent() throws Exception {
        Document document = parse(FXML_PATH);
        Map<String, String> ids = new LinkedHashMap<>();
        Set<String> actions = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);
        collectActions(document.getDocumentElement(), actions);

        for (String id : Set.of(
                "pendingExamTable", "examCodeColumn", "titleColumn", "courseColumn",
                "subjectColumn", "creatorColumn", "versionColumn", "totalColumn",
                "submittedColumn", "statusColumn", "examIdValue", "examCodeValue",
                "titleValue", "courseSubjectValue", "creatorValue", "versionValue",
                "durationValue", "totalValue", "statusValue", "submittedValue",
                "teacherNotesArea", "studentInstructionsArea", "questionTable",
                "questionOrderColumn", "questionIdColumn", "questionVersionColumn",
                "questionContentColumn", "questionTopicColumn",
                "questionDifficultyColumn", "questionScoreColumn", "option1Column",
                "option2Column", "option3Column", "option4Column",
                "correctOptionColumn", "refreshButton", "openButton", "approveButton",
                "rejectButton", "backButton", "feedbackLabel"
        )) {
            assertTrue("Missing required fx:id: " + id, ids.containsKey(id));
        }
        for (String action : Set.of(
                "handleRefresh", "handleOpen", "handleApprove", "handleReject",
                "handleBack"
        )) {
            assertTrue("Missing required action: " + action, actions.contains(action));
        }
    }

    @Test
    public void controllerUsesPendingSnapshotAndExplicitDecisionApisOnly() throws Exception {
        String source = Files.readString(CONTROLLER_PATH);

        assertTrue(source.contains("new ExamClientController(client)"));
        assertTrue(source.contains(".getPendingExams()"));
        assertTrue(source.contains(".getPendingExam(examId)"));
        assertTrue(source.contains("new ExamVersionPayload("));
        assertTrue(source.contains("loadedExam.getExamId()"));
        assertTrue(source.contains("loadedExam.getVersionNo()"));
        assertTrue(source.contains(".approveExam(payload)"));
        assertTrue(source.contains("String trimmedReason = reason.trim()"));
        assertTrue(source.contains("new RejectExamPayload("));
        assertTrue(source.contains(".rejectExam(payload)"));
        assertTrue(source.contains("ExamQuestionDTO"));
        assertFalse(source.contains("import hsts.common.QuestionDTO;"));
        assertFalse(source.contains(".submitExamForApproval("));
        assertFalse(source.contains("new Client("));
        assertFalse(source.contains("client.close("));
        assertFalse(source.contains("authenticatedUserId"));
        assertFalse(source.contains("getCreatedByUserId()"));
        assertTrue(source.contains("Platform.runLater("));
    }

    @Test
    public void snapshotOrderingAndReviewStatusAreDeterministic() {
        ExamQuestionDTO second = question(22, 2);
        ExamQuestionDTO first = question(11, 1);

        List<ExamQuestionDTO> ordered =
                ApprovalRequestsPage.orderedSnapshots(List.of(second, first));

        assertSame(first, ordered.get(0));
        assertSame(second, ordered.get(1));
        assertTrue(ApprovalRequestsPage.canReview(ExamStatus.PENDING_APPROVAL));
        assertFalse(ApprovalRequestsPage.canReview(ExamStatus.DRAFT));
        assertFalse(ApprovalRequestsPage.canReview(ExamStatus.APPROVED));
        assertFalse(ApprovalRequestsPage.canReview(ExamStatus.REJECTED));
    }

    @Test
    public void dashboardAndMainClientKeepApprovalCoordinatorOnlyAndShareClient()
            throws Exception {
        Document dashboardFxml = parse(DASHBOARD_FXML_PATH);
        Element approvalButton = findByFxId(
                dashboardFxml.getDocumentElement(),
                "approvalRequestsButton"
        );
        assertEquals("#handleApprovalRequests", approvalButton.getAttribute("onAction"));

        String dashboardSource = Files.readString(DASHBOARD_PATH);
        assertTrue(dashboardSource.contains("loginResult.getRole() == UserRole.COORDINATOR"));
        assertTrue(dashboardSource.contains("approvalRequestsButton.setVisible(coordinator)"));
        assertTrue(dashboardSource.contains("approvalRequestsButton.setManaged(coordinator)"));
        assertTrue(dashboardSource.contains(
                "approvalRequestsButton.setDisable(!coordinator || approvalRequestsHandler == null)"
        ));

        String mainSource = Files.readString(MAIN_CLIENT_PATH);
        String navigation = methodSource(mainSource, "private void showApprovalRequests(");
        assertTrue(navigation.contains("loginResult.getRole() != UserRole.COORDINATOR"));
        assertTrue(navigation.contains("/hsts/client/boundary/approval-requests-page.fxml"));
        assertTrue(navigation.contains("controller.configure(stage, client"));
        assertTrue(navigation.contains("returnToTeacherDashboard(loginResult)"));
        assertFalse(navigation.contains("new Client("));
        assertFalse(navigation.contains("closeQuietly("));
    }

    private static ExamQuestionDTO question(int id, int order) {
        return new ExamQuestionDTO(
                id, 4, order, 50, "Question " + id, "Topic", "MEDIUM", "",
                "A", "B", "C", "D", 1
        );
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static void collectIds(Element element, Map<String, String> ids) {
        String id = element.getAttribute("fx:id");
        if (!id.isEmpty()) {
            assertFalse("Duplicate fx:id: " + id, ids.containsKey(id));
            ids.put(id, element.getTagName());
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
}
