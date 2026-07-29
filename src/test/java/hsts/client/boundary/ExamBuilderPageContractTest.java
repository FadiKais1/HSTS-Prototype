package hsts.client.boundary;

import hsts.common.ExamQuestionDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.QuestionDTO;
import hsts.common.type.ExamStatus;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExamBuilderPageContractTest {
    private static final Path FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/exam-builder-page.fxml"
    );
    private static final Path CONTROLLER_PATH = Path.of(
            "src/main/java/hsts/client/boundary/ExamBuilderPage.java"
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
                "hsts.client.boundary.ExamBuilderPage",
                document.getDocumentElement().getAttribute("fx:controller")
        );
    }

    @Test
    public void everyFxIdIsUniqueAndMapsToCompatibleControllerField() throws Exception {
        Document document = parse(FXML_PATH);
        Map<String, String> ids = new LinkedHashMap<>();
        collectIds(document.getDocumentElement(), ids);

        for (Map.Entry<String, String> entry : ids.entrySet()) {
            Field field = ExamBuilderPage.class.getDeclaredField(entry.getKey());
            assertEquals(entry.getValue(), field.getType().getSimpleName());
        }
    }

    @Test
    public void everyActionExistsAndRequiredWorkflowControlsArePresent() throws Exception {
        Document document = parse(FXML_PATH);
        Map<String, String> ids = new LinkedHashMap<>();
        Set<String> actions = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);
        collectActions(document.getDocumentElement(), actions);
        Set<String> methods = new HashSet<>();
        for (Method method : ExamBuilderPage.class.getDeclaredMethods()) {
            methods.add(method.getName());
        }
        for (String action : actions) {
            assertTrue("Missing controller action: " + action, methods.contains(action));
        }

        for (String id : Set.of(
                "courseComboBox", "examTable", "examCodeColumn", "examTitleColumn",
                "examCourseColumn", "examVersionColumn", "examTotalColumn",
                "examStatusColumn", "titleField", "durationField", "teacherNotesArea",
                "studentInstructionsArea", "availableQuestionsTable",
                "selectedQuestionsTable", "selectedScoreColumn", "totalScoreLabel",
                "newButton", "openButton", "saveButton", "submitButton",
                "refreshButton", "backButton", "addButton", "removeButton",
                "moveUpButton", "moveDownButton"
        )) {
            assertTrue("Missing required fx:id: " + id, ids.containsKey(id));
        }
        for (String action : Set.of(
                "handleNewExam", "handleOpenExam", "handleSaveDraft",
                "handleSubmitForApproval", "handleRefresh", "handleBack",
                "handleAddQuestion", "handleRemoveQuestion", "handleMoveUp",
                "handleMoveDown"
        )) {
            assertTrue("Missing required action: " + action, actions.contains(action));
        }
    }

    @Test
    public void controllerUsesAuthenticatedApisAndDoesNotOwnClientLifecycle() throws Exception {
        String source = Files.readString(CONTROLLER_PATH);

        assertTrue(source.contains("new ExamClientController(client)"));
        assertTrue(source.contains("new QuestionClientController(client)"));
        assertTrue(source.contains(".getMyCourses()"));
        assertTrue(source.contains(".getMyExams()"));
        assertTrue(source.contains(".listQuestions(filter)"));
        assertTrue(source.contains("QuestionStatus.ACTIVE"));
        assertFalse(source.contains(".getAllQuestions("));
        assertTrue(source.contains("new CreateExamPayload("));
        assertTrue(source.contains("new UpdateExamPayload("));
        assertTrue(source.contains("loadedExam.getVersionNo()"));
        assertTrue(source.contains("new ExamVersionPayload("));
        assertTrue(source.contains(".submitExamForApproval(payload)"));
        assertFalse(source.contains(".approveExam("));
        assertFalse(source.contains("new Client("));
        assertFalse(source.contains("client.close("));
        assertFalse(source.contains("authenticatedUserId"));
        assertTrue(source.contains("Platform.runLater("));
    }

    @Test
    public void payloadOrderVersionScoreAndReadOnlyRulesAreDeterministic() {
        QuestionDTO firstQuestion = question(11, 4, "First");
        ExamQuestionDTO secondSnapshot = new ExamQuestionDTO(
                12, 7, 9, 60, "Second snapshot", "Topic", "HARD", "",
                "A", "B", "C", "D", 2
        );
        ExamBuilderPage.SelectedQuestionItem first =
                ExamBuilderPage.SelectedQuestionItem.fromQuestion(firstQuestion);
        first.setScore(40);
        ExamBuilderPage.SelectedQuestionItem second =
                ExamBuilderPage.SelectedQuestionItem.fromSnapshot(secondSnapshot);

        List<ExamQuestionSelectionPayload> payloads =
                ExamBuilderPage.buildSelectionPayloads(List.of(first, second));

        assertEquals(2, payloads.size());
        assertEquals(11, payloads.get(0).getQuestionId());
        assertEquals(4, payloads.get(0).getQuestionVersionNo());
        assertEquals(1, payloads.get(0).getOrderNumber());
        assertEquals(40, payloads.get(0).getScore(), 0);
        assertEquals(12, payloads.get(1).getQuestionId());
        assertEquals(7, payloads.get(1).getQuestionVersionNo());
        assertEquals(2, payloads.get(1).getOrderNumber());
        assertEquals(60, payloads.get(1).getScore(), 0);
        assertEquals("Second snapshot", second.getContent());
        assertEquals("Topic", second.getTopic());
        assertEquals("HARD", second.getDifficulty());
        assertNull(second.getIllustration());
        assertEquals("A", second.getAnswerOption1());
        assertEquals("B", second.getAnswerOption2());
        assertEquals("C", second.getAnswerOption3());
        assertEquals("D", second.getAnswerOption4());
        assertEquals(2, second.getCorrectOptionNumber());
        assertEquals(new BigDecimal("100.0"),
                ExamBuilderPage.calculateTotal(List.of(first, second)));
        assertTrue(ExamBuilderPage.isReadOnly(ExamStatus.PENDING_APPROVAL));
        assertFalse(ExamBuilderPage.isReadOnly(ExamStatus.DRAFT));
        assertFalse(ExamBuilderPage.isReadOnly(ExamStatus.REJECTED));
        assertFalse(ExamBuilderPage.isReadOnly(ExamStatus.APPROVED));
    }

    @Test
    public void dashboardAndMainClientConfigureExamManagementWithSharedClient() throws Exception {
        Document dashboard = parse(DASHBOARD_FXML_PATH);
        Map<String, String> ids = new LinkedHashMap<>();
        Set<String> actions = new HashSet<>();
        collectIds(dashboard.getDocumentElement(), ids);
        collectActions(dashboard.getDocumentElement(), actions);
        Element examButton = findByFxId(dashboard.getDocumentElement(), "examManagementButton");

        for (Map.Entry<String, String> entry : ids.entrySet()) {
            Field field = TeacherDashboard.class.getDeclaredField(entry.getKey());
            assertEquals(entry.getValue(), field.getType().getSimpleName());
        }
        Set<String> dashboardMethods = new HashSet<>();
        for (Method method : TeacherDashboard.class.getDeclaredMethods()) {
            dashboardMethods.add(method.getName());
        }
        for (String action : actions) {
            assertTrue("Missing dashboard action: " + action,
                    dashboardMethods.contains(action));
        }

        assertTrue(ids.containsKey("examManagementButton"));
        assertTrue(actions.contains("handleExamManagement"));
        assertFalse(Boolean.parseBoolean(examButton.getAttribute("disable")));

        String source = Files.readString(MAIN_CLIENT_PATH);
        String navigationMethod = methodSource(
                source,
                "private void showExamBuilder(LoginResult loginResult,"
        );
        assertTrue(navigationMethod.contains("/hsts/client/boundary/exam-builder-page.fxml"));
        assertTrue(navigationMethod.contains("controller.configure("));
        assertTrue(navigationMethod.contains("stage,"));
        assertTrue(navigationMethod.contains("client,"));
        assertTrue(navigationMethod.contains("returnToTeacherDashboard(loginResult)"));
        assertFalse(navigationMethod.contains("new Client("));
        assertFalse(navigationMethod.contains("closeQuietly("));
    }

    @Test
    public void rejectedReasonAndQuestionBankStatePreservationAreReachable() throws Exception {
        String controller = Files.readString(CONTROLLER_PATH);
        String fxml = Files.readString(FXML_PATH);
        String main = Files.readString(MAIN_CLIENT_PATH);

        assertTrue(fxml.contains("text=\"Rejection reason\""));
        assertTrue(fxml.contains("fx:id=\"rejectionReasonLabel\""));
        assertTrue(fxml.contains("text=\"Open Question Bank\""));
        assertTrue(controller.contains("loaded.getStatus() == ExamStatus.REJECTED"));
        assertTrue(controller.contains("loaded.getRejectionReason()"));
        assertTrue(controller.contains("snapshotEditorState()"));
        assertTrue(controller.contains("restoreEditorState(state)"));
        assertTrue(controller.contains("SelectedQuestionItem::copy"));
        assertTrue(controller.contains("confirmDiscardIfDirty()"));
        assertTrue(main.contains("showQuestionBankFromExamBuilder("));
        assertTrue(main.contains("controller.snapshotEditorState()"));
        assertTrue(main.contains("showExamBuilder(loginResult, editorState)"));
        assertFalse(controller.contains("examClientController.saveDraft"));
    }

    private static QuestionDTO question(int id, int version, String content) {
        return new QuestionDTO(
                id, content, "Topic", "MULTIPLE_CHOICE", "MEDIUM", "ACTIVE", "",
                "A", "B", "C", "D", 1, 7, 3, version
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
