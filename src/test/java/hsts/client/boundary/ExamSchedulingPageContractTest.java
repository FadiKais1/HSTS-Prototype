package hsts.client.boundary;

import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.type.ExamStatus;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.UserRole;
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

public class ExamSchedulingPageContractTest {
    private static final Path FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/exam-scheduling-page.fxml"
    );
    private static final Path CONTROLLER_PATH = Path.of(
            "src/main/java/hsts/client/boundary/ExamSchedulingPage.java"
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
    private static final LocalDateTime OPENING =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime CLOSING = OPENING.plusHours(2);

    @Test
    public void fxmlIsWellFormedUsesExpectedControllerAndMatchesFieldsAndActions()
            throws Exception {
        Document document = parse(FXML_PATH);
        assertEquals(
                "hsts.client.boundary.ExamSchedulingPage",
                document.getDocumentElement().getAttribute("fx:controller")
        );

        Map<String, String> ids = new LinkedHashMap<>();
        Set<String> actions = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);
        collectActions(document.getDocumentElement(), actions);
        for (Map.Entry<String, String> entry : ids.entrySet()) {
            Field field = ExamSchedulingPage.class.getDeclaredField(entry.getKey());
            assertEquals(entry.getValue(), field.getType().getSimpleName());
        }

        Set<String> methods = new HashSet<>();
        for (Method method : ExamSchedulingPage.class.getDeclaredMethods()) {
            methods.add(method.getName());
        }
        for (String action : actions) {
            assertTrue("Missing action: " + action, methods.contains(action));
        }
        for (String id : Set.of(
                "userLabel", "roleLabel", "approvedExamComboBox",
                "openingDatePicker", "openingHourSpinner", "openingMinuteSpinner",
                "closingDatePicker", "closingHourSpinner", "closingMinuteSpinner",
                "scheduleButton", "refreshButton", "backButton", "busyIndicator",
                "feedbackLabel", "generatedCodeLabel", "generatedDetailsLabel",
                "executionTable", "executionIdColumn", "examTitleColumn",
                "examCodeColumn", "versionColumn", "executionCodeColumn",
                "openingColumn", "closingColumn", "durationColumn", "statusColumn"
        )) {
            assertTrue("Missing required fx:id: " + id, ids.containsKey(id));
        }
        assertEquals(
                Set.of("handleSchedule", "handleRefresh", "handleBack"),
                actions
        );
    }

    @Test
    public void approvedFilteringPreservesServerOrderAndExcludesOtherStatuses() {
        ExamSummaryDTO draft = exam(1, 1, ExamStatus.DRAFT);
        ExamSummaryDTO approvedFirst = exam(2, 4, ExamStatus.APPROVED);
        ExamSummaryDTO rejected = exam(3, 2, ExamStatus.REJECTED);
        ExamSummaryDTO approvedSecond = exam(4, 7, ExamStatus.APPROVED);
        ExamSummaryDTO pending = exam(5, 3, ExamStatus.PENDING_APPROVAL);

        List<ExamSummaryDTO> result = ExamSchedulingPage.approvedOnly(List.of(
                draft, approvedFirst, rejected, approvedSecond, pending
        ));

        assertEquals(2, result.size());
        assertSame(approvedFirst, result.get(0));
        assertSame(approvedSecond, result.get(1));
    }

    @Test
    public void schedulePayloadUsesSelectedDtoIdentityAndEveryTimeFieldDirectly() {
        ExamSummaryDTO selected = exam(42, 9, ExamStatus.APPROVED);

        ScheduleExamExecutionPayload payload = ExamSchedulingPage.buildSchedulePayload(
                selected,
                OPENING,
                CLOSING
        );

        assertEquals(42, payload.getExamId());
        assertEquals(9, payload.getExamVersionNo());
        assertSame(OPENING, payload.getOpeningTime());
        assertSame(CLOSING, payload.getClosingTime());
    }

    @Test
    public void roleBusyAndStaleGuardsAreDeterministic() {
        ExamSummaryDTO approved = exam(42, 9, ExamStatus.APPROVED);
        assertTrue(ExamSchedulingPage.isSchedulingRole(UserRole.TEACHER));
        assertTrue(ExamSchedulingPage.isSchedulingRole(UserRole.COORDINATOR));
        assertFalse(ExamSchedulingPage.isSchedulingRole(UserRole.STUDENT));
        assertFalse(ExamSchedulingPage.isSchedulingRole(UserRole.PRINCIPAL));

        assertTrue(ExamSchedulingPage.canStartScheduling(false, false, approved));
        assertFalse(ExamSchedulingPage.canStartScheduling(false, true, approved));
        assertFalse(ExamSchedulingPage.canStartScheduling(true, false, approved));
        assertFalse(ExamSchedulingPage.canStartScheduling(false, false, null));

        assertFalse(ExamSchedulingPage.isStale(false, 3, 3));
        assertTrue(ExamSchedulingPage.isStale(false, 2, 3));
        assertTrue(ExamSchedulingPage.isStale(true, 3, 3));
    }

    @Test
    public void serverMessagesArePreservedAndBlankFailuresUseSafeFallback() {
        assertEquals(
                "Exam version is not approved or accessible",
                ExamSchedulingPage.cleanError(new CompletionException(
                        new IllegalStateException(
                                "Exam version is not approved or accessible"
                        )
                ))
        );
        assertEquals(
                "Unable to complete the request",
                ExamSchedulingPage.cleanError(new CompletionException(
                        new IllegalStateException("  ")
                ))
        );
    }

    @Test
    public void pageUsesSharedControllersAsyncRefreshAndSafeNavigation() throws Exception {
        String source = Files.readString(CONTROLLER_PATH);

        assertTrue(source.contains("new ExamClientController(client)"));
        assertTrue(source.contains("new ExamExecutionClientController(client)"));
        assertTrue(source.contains(".getMyExams()"));
        assertTrue(source.contains(".getMyExamExecutions()"));
        assertTrue(source.contains(".scheduleExamExecution(payload)"));
        assertTrue(source.contains("Platform.runLater("));
        assertTrue(source.contains("canStartScheduling(closed, scheduling, exam)"));
        assertTrue(source.contains("showGeneratedExecution(scheduled)"));
        assertTrue(source.contains("loadExecutions("));
        assertTrue(source.contains("scheduleRequestGeneration++"));
        assertTrue(source.contains("examRequestGeneration++"));
        assertTrue(source.contains("executionRequestGeneration++"));
        assertFalse(source.contains("new Client("));
        assertFalse(source.contains("client.close("));
        assertFalse(source.contains("disconnect("));
        assertFalse(source.contains("RequestType.LOGOUT"));
        assertFalse(source.contains("getCreatedByUserId()"));
    }

    @Test
    public void dashboardAndMainClientExposeManagerSchedulingWithSharedClient()
            throws Exception {
        Document dashboard = parse(DASHBOARD_FXML_PATH);
        Element schedulingButton = findByFxId(
                dashboard.getDocumentElement(),
                "examSchedulingButton"
        );
        assertEquals("#handleExamScheduling", schedulingButton.getAttribute("onAction"));

        String dashboardSource = Files.readString(DASHBOARD_PATH);
        assertTrue(dashboardSource.contains("role == UserRole.TEACHER"));
        assertTrue(dashboardSource.contains("role == UserRole.COORDINATOR"));
        assertTrue(dashboardSource.contains("examSchedulingHandler.run()"));
        assertTrue(dashboardSource.contains("examSchedulingButton.setVisible(manager)"));
        assertTrue(dashboardSource.contains("examSchedulingButton.setManaged(manager)"));

        String mainSource = Files.readString(MAIN_CLIENT_PATH);
        String navigation = methodSource(mainSource, "private void showExamScheduling(");
        assertTrue(navigation.contains(
                "/hsts/client/boundary/exam-scheduling-page.fxml"
        ));
        assertTrue(navigation.contains("controller.configure("));
        assertTrue(navigation.contains("stage"));
        assertTrue(navigation.contains("client"));
        assertTrue(navigation.contains("loginResult"));
        assertTrue(navigation.contains("returnToTeacherDashboard(loginResult)"));
        assertFalse(navigation.contains("new Client("));
        assertFalse(navigation.contains("closeQuietly("));
        assertFalse(navigation.contains("logout("));
    }

    @Test
    public void returnedExecutionDtoProvidesDisplayedGeneratedAndTableDetails() {
        ExamExecutionSummaryDTO execution = execution();
        assertEquals(81, execution.getExecutionId());
        assertEquals("A1B2", execution.getExecutionCode());
        assertEquals("Approved Midterm", execution.getExamTitle());
        assertEquals("EX1234", execution.getExamCode());
        assertEquals(3, execution.getExamVersionNo());
        assertEquals(75, execution.getDurationMinutes());
        assertEquals(ExecutionStatus.SCHEDULED, execution.getStatus());
        assertSame(OPENING, execution.getOpeningTime());
        assertSame(CLOSING, execution.getClosingTime());
    }

    private static ExamSummaryDTO exam(int examId, int version, ExamStatus status) {
        return new ExamSummaryDTO(
                examId, "EX" + examId, 7, "Mathematics", 3, "Mathematics",
                1002, "Teacher", version, "Exam " + examId, 75, 100,
                status, OPENING.minusDays(1), null, null, null
        );
    }

    private static ExamExecutionSummaryDTO execution() {
        return new ExamExecutionSummaryDTO(
                81, "A1B2", 40, 3, "EX1234", "Approved Midterm",
                7, "Mathematics", OPENING, CLOSING, 75,
                ExecutionStatus.SCHEDULED, 1002, "Teacher", OPENING.minusDays(1),
                0, 0, 0
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
