package hsts.client.boundary;

import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.StartExamPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.StudentExamQuestionDTO;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;
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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ExamExecutionPageContractTest {
    private static final Path FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/exam-execution-page.fxml"
    );
    private static final Path CONTROLLER_PATH = Path.of(
            "src/main/java/hsts/client/boundary/ExamExecutionPage.java"
    );
    private static final Path DASHBOARD_PATH = Path.of(
            "src/main/java/hsts/client/boundary/StudentDashboard.java"
    );
    private static final Path DASHBOARD_FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/student-dashboard.fxml"
    );
    private static final Path MAIN_CLIENT_PATH = Path.of(
            "src/main/java/hsts/client/MainClient.java"
    );
    private static final LocalDateTime OPENING =
            LocalDateTime.of(2026, 8, 1, 9, 0);

    @Test
    public void fxmlIsWellFormedAndEveryIdAndActionMatchesController() throws Exception {
        Document document = parse(FXML_PATH);
        assertEquals(
                "hsts.client.boundary.ExamExecutionPage",
                document.getDocumentElement().getAttribute("fx:controller")
        );

        Map<String, String> ids = new LinkedHashMap<>();
        Set<String> actions = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);
        collectActions(document.getDocumentElement(), actions);
        for (Map.Entry<String, String> entry : ids.entrySet()) {
            Field field = ExamExecutionPage.class.getDeclaredField(entry.getKey());
            assertEquals(entry.getValue(), field.getType().getSimpleName());
        }

        Set<String> methods = new HashSet<>();
        for (Method method : ExamExecutionPage.class.getDeclaredMethods()) {
            methods.add(method.getName());
        }
        for (String action : actions) {
            assertTrue("Missing action: " + action, methods.contains(action));
        }
        assertEquals(Set.of(
                "handleBack", "handleValidateCode", "handleStartExam",
                "handlePreviousQuestion", "handleNextQuestion",
                "handleRefreshAttempt", "handleSubmitExam"
        ), actions);
    }

    @Test
    public void fxmlContainsCompleteEntryAttemptAndSubmissionControls() throws Exception {
        Document document = parse(FXML_PATH);
        Map<String, String> ids = new LinkedHashMap<>();
        collectIds(document.getDocumentElement(), ids);

        for (String id : Set.of(
                "executionCodeField", "validateButton", "previewPane",
                "previewExamTitleLabel", "previewCourseLabel", "previewWindowLabel",
                "previewDurationLabel", "previewStatusLabel", "identityField",
                "startButton", "backButton", "feedbackLabel", "attemptPane",
                "examTitleLabel", "attemptDetailsLabel", "instructionsLabel",
                "submissionStatusLabel", "countdownLabel", "progressLabel",
                "questionListView", "questionPositionLabel", "questionTextLabel",
                "questionDetailsLabel", "option1Radio", "option2Radio",
                "option3Radio", "option4Radio", "previousButton", "nextButton",
                "refreshButton", "submitButton", "saveStatusLabel"
        )) {
            assertTrue("Missing required fx:id: " + id, ids.containsKey(id));
        }
        assertEquals("PasswordField", ids.get("identityField"));
    }

    @Test
    public void dashboardAndMainClientExposeStudentOnlySharedClientNavigation()
            throws Exception {
        Document dashboard = parse(DASHBOARD_FXML_PATH);
        Element examButton = findByFxId(
                dashboard.getDocumentElement(),
                "examExecutionButton"
        );
        assertEquals("#handleExamExecution", examButton.getAttribute("onAction"));

        String dashboardSource = Files.readString(DASHBOARD_PATH);
        assertTrue(dashboardSource.contains("loginResult.getRole() != UserRole.STUDENT"));
        assertTrue(dashboardSource.contains("examExecutionHandler.run()"));
        assertTrue(dashboardSource.contains("examExecutionButton.setVisible(student)"));
        assertTrue(dashboardSource.contains("examExecutionButton.setManaged(student)"));

        String mainSource = Files.readString(MAIN_CLIENT_PATH);
        String navigation = methodSource(mainSource, "private void showExamExecution(");
        assertTrue(navigation.contains("loginResult.getRole() != UserRole.STUDENT"));
        assertTrue(navigation.contains(
                "/hsts/client/boundary/exam-execution-page.fxml"
        ));
        assertTrue(navigation.contains("controller.configure("));
        assertTrue(navigation.contains("stage"));
        assertTrue(navigation.contains("client"));
        assertTrue(navigation.contains("loginResult"));
        assertTrue(navigation.contains("returnToStudentDashboard(loginResult)"));
        assertFalse(navigation.contains("new Client("));
        assertFalse(navigation.contains("closeQuietly("));
        assertFalse(navigation.contains("logout("));
    }

    @Test
    public void validationAndStartPayloadsUseNormalizedValidatedExecutionIdentity() {
        assertEquals(
                "A1B2",
                ExamExecutionPage.buildExecutionCodePayload("  a1b2 ")
                        .getExecutionCode()
        );
        ExamExecutionPreviewDTO preview = preview(81, "A1B2");

        StartExamPayload payload = ExamExecutionPage.buildStartPayload(
                preview,
                " exact identity input "
        );

        assertEquals(81, payload.getExecutionId());
        assertEquals(" exact identity input ", payload.getIdentityConfirmation());
    }

    @Test
    public void identityIsClearedOnTheSingleSuccessAndFailureCompletionPath()
            throws Exception {
        String source = Files.readString(CONTROLLER_PATH);
        String start = methodSource(source, "private void handleStartExam()");
        int completion = start.indexOf(".whenComplete(");
        int clear = start.indexOf("clearIdentityInput();", completion);
        int staleGuard = start.indexOf("isStale(", completion);

        assertTrue(completion >= 0);
        assertTrue(clear > completion);
        assertTrue(clear < staleGuard);
        assertFalse(source.contains("private String identity"));
    }

    @Test
    public void questionsOptionsAndRestoredAnswersPreserveServerValuesAndOrder() {
        StudentExamQuestionDTO second = question(22, 2, "2A", "2B", "2C", "2D");
        StudentExamQuestionDTO first = question(11, 1, "1A", "1B", "1C", "1D");
        List<StudentExamQuestionDTO> ordered =
                ExamExecutionPage.preserveQuestionOrder(List.of(second, first));

        assertSame(second, ordered.get(0));
        assertSame(first, ordered.get(1));
        assertEquals(List.of("2A", "2B", "2C", "2D"),
                ExamExecutionPage.optionTexts(second));
        Map<Integer, Integer> restored = ExamExecutionPage.restoreSelections(List.of(
                new StudentAnswerDTO(22, 4, OPENING),
                new StudentAnswerDTO(11, 2, OPENING.plusMinutes(1))
        ));
        assertEquals(Integer.valueOf(4), restored.get(22));
        assertEquals(Integer.valueOf(2), restored.get(11));
        assertEquals(2, ExamExecutionPage.answeredCount(ordered, restored));
    }

    @Test
    public void savePayloadUsesDtoQuestionIdAndSubmissionIdWithoutParsingLabels() {
        StudentExamQuestionDTO question = question(42, 7, "A", "B", "C", "D");

        SaveExamAnswerPayload payload = ExamExecutionPage.buildSavePayload(
                501,
                question,
                3
        );

        assertEquals(501, payload.getSubmissionId());
        assertEquals(42, payload.getQuestionId());
        assertEquals(3, payload.getSelectedOptionNumber());
    }

    @Test
    public void answerStatePreventsDuplicatesRestoresFailureAndIgnoresStaleCallbacks() {
        ExamExecutionPage.ConfirmedAnswerState state =
                new ExamExecutionPage.ConfirmedAnswerState();
        state.reset(Map.of(11, 2));

        long first = state.begin(11);
        assertTrue(first > 0);
        assertEquals(-1, state.begin(11));
        assertEquals(Integer.valueOf(2), state.fail(11, first + 1));
        assertTrue(state.isPending(11));
        assertFalse(state.confirm(11, 4, first + 1));
        assertEquals(Integer.valueOf(2), state.fail(11, first));
        assertFalse(state.isPending(11));

        long retry = state.begin(11);
        assertTrue(state.confirm(11, 4, retry));
        assertEquals(Integer.valueOf(4), state.confirmed(11));
    }

    @Test
    public void navigationLabelsTrackOnlyConfirmedAndPendingAnswerState() {
        List<StudentExamQuestionDTO> questions = List.of(
                question(11, 1, "A", "B", "C", "D"),
                question(22, 2, "A", "B", "C", "D"),
                question(33, 3, "A", "B", "C", "D")
        );

        assertEquals(List.of(
                "Question 1 - Answered",
                "Question 2 - Saving",
                "Question 3 - Unanswered"
        ), ExamExecutionPage.questionNavigationLabels(
                questions,
                Map.of(11, 2),
                Set.of(22)
        ));
    }

    @Test
    public void countdownUsesDeadlineNeverGoesNegativeAndControlsEditability() {
        LocalDateTime now = OPENING;
        assertEquals(61, ExamExecutionPage.remainingSeconds(
                now.plusSeconds(60).plusNanos(1_000_000), now
        ));
        assertEquals(0, ExamExecutionPage.remainingSeconds(now.minusSeconds(1), now));
        assertEquals("01:01:01", ExamExecutionPage.formatRemainingTime(3661));
        assertEquals("00:00:00", ExamExecutionPage.formatRemainingTime(-10));
        assertTrue(ExamExecutionPage.isEditable(SubmissionStatus.IN_PROGRESS, 1));
        assertFalse(ExamExecutionPage.isEditable(SubmissionStatus.IN_PROGRESS, 0));
        assertFalse(ExamExecutionPage.isEditable(SubmissionStatus.SUBMITTED, 100));
    }

    @Test
    public void staleGuardsAndNoActiveAttemptHandlingAreDeterministic() {
        assertFalse(ExamExecutionPage.isStale(false, 4, 4, 7, 7));
        assertTrue(ExamExecutionPage.isStale(true, 4, 4, 7, 7));
        assertTrue(ExamExecutionPage.isStale(false, 3, 4, 7, 7));
        assertTrue(ExamExecutionPage.isStale(false, 4, 4, 6, 7));
        assertTrue(ExamExecutionPage.isNoActiveAttempt(new CompletionException(
                new IllegalStateException("Invalid active-attempt response from server")
        )));
        assertFalse(ExamExecutionPage.isNoActiveAttempt(new CompletionException(
                new IllegalStateException("Execution not available")
        )));
    }

    @Test
    public void serverErrorsArePreservedAndBlankFailuresUseSafeFallback() {
        assertEquals(
                "Invalid identity confirmation",
                ExamExecutionPage.cleanError(new CompletionException(
                        new IllegalStateException("Invalid identity confirmation")
                ))
        );
        assertEquals(
                "Unable to complete the request",
                ExamExecutionPage.cleanError(new CompletionException(
                        new IllegalStateException("  ")
                ))
        );
    }

    @Test
    public void sourceUsesAsyncControllerAndContainsNoSensitiveOrDirectNetworkingPath()
            throws Exception {
        String source = Files.readString(CONTROLLER_PATH);
        String fxml = Files.readString(FXML_PATH);

        assertTrue(source.contains("new ExamExecutionClientController(client)"));
        assertTrue(source.contains(".validateExecutionCode(payload)"));
        assertTrue(source.contains(".startExamAttempt(payload)"));
        assertTrue(source.contains(".saveExamAnswer(payload)"));
        assertTrue(source.contains(".getActiveExamAttempt("));
        assertTrue(source.contains(".submitExamAttempt("));
        assertTrue(source.contains("Platform.runLater("));
        assertTrue(source.contains("deadlineActionTriggered"));
        assertEquals(1, occurrences(source, "new Timeline("));
        assertFalse(source.contains("new Client("));
        assertFalse(source.contains("client.sendRequest"));
        assertFalse(source.contains("client.close("));
        assertFalse(source.contains("disconnect("));
        assertFalse(source.contains("RequestType.LOGOUT"));
        assertFalse(source.contains("getCorrectOption"));
        assertFalse(source.contains("getScore()"));
        assertFalse(source.contains("getFinalScore"));
        assertFalse(source.contains("getAutomaticScore"));
        assertFalse(fxml.toLowerCase().contains("correct answer"));
        assertFalse(fxml.toLowerCase().contains("grade"));
        assertFalse(fxml.toLowerCase().contains("score"));
        assertFalse(fxml.contains("123456"));
    }

    @Test
    public void submissionIsConfirmedUsesSubmissionPayloadAndMakesResultReadOnly()
            throws Exception {
        String source = Files.readString(CONTROLLER_PATH);
        String submitHandler = methodSource(source, "private void handleSubmitExam()");
        String submitRequest = methodSource(source, "private void submitCurrentAttempt(");

        assertTrue(submitHandler.contains("Alert.AlertType.CONFIRMATION"));
        assertTrue(submitHandler.contains("ButtonType.OK"));
        assertTrue(submitRequest.contains("new SubmissionIdPayload(submissionId)"));
        assertTrue(submitRequest.contains("applyAttempt(submittedAttempt)"));
        assertTrue(source.contains("submissionStatus != SubmissionStatus.IN_PROGRESS"));
    }

    @Test
    public void backNavigationConfirmsInProgressAndNeverSubmitsOrLogsOut() throws Exception {
        String source = Files.readString(CONTROLLER_PATH);
        String back = methodSource(source, "private void handleBack()");

        assertTrue(back.contains("submissionStatus == SubmissionStatus.IN_PROGRESS"));
        assertTrue(back.contains("Alert.AlertType.CONFIRMATION"));
        assertTrue(back.contains("stopCountdown()"));
        assertTrue(back.contains("invalidateCallbacks()"));
        assertTrue(back.contains("navigation.run()"));
        assertFalse(back.contains("submitCurrentAttempt"));
        assertFalse(back.contains("submitExamAttempt"));
        assertFalse(back.contains("logout"));
        assertFalse(back.contains("client.close"));
        assertFalse(back.contains("disconnect"));
    }

    private static ExamExecutionPreviewDTO preview(int executionId, String code) {
        return new ExamExecutionPreviewDTO(
                executionId, code, 40, 3, "Approved Midterm", 7,
                "Mathematics", OPENING, OPENING.plusHours(2), 75,
                ExecutionStatus.OPEN, false
        );
    }

    private static StudentExamQuestionDTO question(int id, int order,
                                                    String option1, String option2,
                                                    String option3, String option4) {
        return new StudentExamQuestionDTO(
                id, 4, order, 25, "Question " + id, "Topic", "MEDIUM", "",
                option1, option2, option3, option4
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

    private static int occurrences(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
