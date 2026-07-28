package hsts.client.boundary;

import hsts.common.ScoreBandDTO;
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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

public class ReportsPageContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/hsts/client/boundary/ReportsPage.java"
    );
    private static final Path FXML = Path.of(
            "src/main/resources/hsts/client/boundary/reports-page.fxml"
    );
    private static final Path MAIN_CLIENT = Path.of(
            "src/main/java/hsts/client/MainClient.java"
    );
    private static final Path TEACHER_DASHBOARD = Path.of(
            "src/main/java/hsts/client/boundary/TeacherDashboard.java"
    );
    private static final Path PRINCIPAL_DASHBOARD = Path.of(
            "src/main/java/hsts/client/boundary/PrincipalDashboard.java"
    );
    private static final Path STUDENT_DASHBOARD = Path.of(
            "src/main/java/hsts/client/boundary/StudentDashboard.java"
    );

    @Test
    public void targetValidationIsExactAndOccursBeforeControllerCalls() throws Exception {
        assertValidation(null, "Target ID is required");
        assertValidation("  ", "Target ID is required");
        assertValidation("abc", "Target ID must be a positive number");
        assertValidation("0", "Target ID must be a positive number");
        assertValidation("-8", "Target ID must be a positive number");
        assertEquals(42, ReportsPage.parseTargetId(" 42 "));

        String source = Files.readString(SOURCE);
        int parse = source.indexOf("targetId = parseTargetId(targetIdField.getText())");
        int teacher = source.indexOf("getTeacherExamsReport(targetId)");
        int course = source.indexOf("getCourseExamsReport(targetId)");
        int student = source.indexOf("getStudentExamsReport(targetId)");
        assertTrue(parse >= 0 && parse < teacher && parse < course && parse < student);
    }

    @Test
    public void formattingPreservesDecimalsNullsAndAllOrderedBands() {
        assertEquals("12.3400", ReportsPage.formatScore(new BigDecimal("12.3400")));
        assertEquals("N/A", ReportsPage.formatScore(null));

        List<ScoreBandDTO> bands = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            bands.add(new ScoreBandDTO(
                    index * 10,
                    index == 9 ? 100 : index * 10 + 9,
                    index == 4 ? 0 : index
            ));
        }
        assertEquals(List.of(
                "0–9", "10–19", "20–29", "30–39", "40–49",
                "50–59", "60–69", "70–79", "80–89", "90–100"
        ), ReportsPage.bandLabels(bands));
        assertEquals(0, bands.get(4).getSubmissionCount());
    }

    @Test
    public void asyncLoadingHasDuplicateDisposalAndGenerationGuards() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("request.whenComplete("));
        assertTrue(source.contains("Platform.runLater("));
        assertTrue(source.contains("if (loading || disposed)"));
        assertTrue(source.contains("long generation = ++requestGeneration"));
        assertTrue(source.contains("isStale(disposed, generation, requestGeneration)"));
        assertTrue(source.contains("disposed = true;"));
        assertTrue(source.contains("requestGeneration++;"));
        assertFalse(source.contains(".join("));
        assertFalse(source.contains(".get("));
        assertTrue(ReportsPage.isStale(true, 2, 2));
        assertTrue(ReportsPage.isStale(false, 1, 2));
        assertFalse(ReportsPage.isStale(false, 2, 2));

        int loadStart = source.indexOf("private void loadReport(");
        int showStart = source.indexOf("private void showReport(");
        String loadMethod = source.substring(loadStart, showStart);
        assertFalse(loadMethod.contains("clearReportDisplay()"));
        assertTrue(loadMethod.contains("cleanError(failure)"));
        assertTrue(loadMethod.contains("INVALID_RESPONSE"));
    }

    @Test
    public void boundaryUsesOnlyApprovedControllerAndReportContracts() throws Exception {
        Method configure = ReportsPage.class.getMethod(
                "configure",
                javafx.stage.Stage.class,
                hsts.client.net.Client.class,
                hsts.common.LoginResult.class,
                Runnable.class
        );
        assertNotNull(configure);
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("new ReportClientController(this.client)"));
        assertTrue(source.contains("getMyAuthoredExamsReport()"));
        assertTrue(source.contains("getTeacherExamsReport(targetId)"));
        assertTrue(source.contains("getCourseExamsReport(targetId)"));
        assertTrue(source.contains("getStudentExamsReport(targetId)"));
        assertFalse(source.contains("getExamExecutionReport("));
        for (String forbidden : List.of(
                "client.sendRequest", "new Client(", "client.close(",
                "client.connect(", "client.disconnect(", "hsts.common.Request",
                "hsts.common.Response", "hsts.server.", "studentId",
                "teacherFeedback", "adjustmentReason", "correctOption",
                "selectedOption", "password", "identityHash", "java.io.File"
        )) {
            assertFalse("Forbidden ReportsPage token: " + forbidden,
                    source.contains(forbidden));
        }
    }

    @Test
    public void dashboardsAndMainClientWireReportsForManagersOnly() throws Exception {
        String teacher = Files.readString(TEACHER_DASHBOARD);
        String principal = Files.readString(PRINCIPAL_DASHBOARD);
        String student = Files.readString(STUDENT_DASHBOARD);
        String main = Files.readString(MAIN_CLIENT);

        assertTrue(teacher.contains("reportsHandler"));
        assertTrue(teacher.contains("handleReports"));
        assertTrue(teacher.contains("UserRole.TEACHER"));
        assertTrue(teacher.contains("UserRole.COORDINATOR"));
        assertTrue(principal.contains("reportsHandler"));
        assertTrue(principal.contains("handleReports"));
        assertFalse(student.contains("reportsHandler"));
        assertFalse(student.contains("handleReports"));

        assertTrue(main.contains("showReports(loginResult)"));
        assertTrue(main.contains("/hsts/client/boundary/reports-page.fxml"));
        assertTrue(main.contains("controller.configure("));
        assertTrue(main.contains("stage,\n                    client,\n                    loginResult"));
        assertTrue(main.contains("returnFromReports(loginResult)"));

        int start = main.indexOf("private void showReports(");
        int end = main.indexOf("private void returnFromReports(");
        String navigation = main.substring(start, end);
        assertFalse(navigation.contains("new Client("));
        assertFalse(navigation.contains(".close("));
        assertFalse(navigation.contains(".connect("));
        assertFalse(navigation.contains(".disconnect("));
    }

    @Test
    public void fxmlIsWellFormedUniqueAndMatchesControllerFieldsAndHandlers()
            throws Exception {
        Document document = parse(FXML);
        assertEquals(
                "hsts.client.boundary.ReportsPage",
                document.getDocumentElement().getAttribute("fx:controller")
        );

        Map<String, Element> ids = new HashMap<>();
        collectIds(document.getDocumentElement(), ids);
        for (String required : List.of(
                "roleContextLabel", "principalControls", "comparisonModeComboBox",
                "targetIdField", "loadButton", "refreshButton", "backButton",
                "loadingIndicator", "statusLabel", "errorLabel", "reportTitleLabel",
                "targetNameLabel", "generatedAtLabel", "executionCountLabel",
                "executionTable", "executionCodeColumn", "examTitleColumn",
                "courseNameColumn", "openingTimeColumn", "closingTimeColumn",
                "publishedCountColumn", "averageScoreColumn", "medianScoreColumn",
                "startedCountColumn", "submittedCountColumn",
                "autoSubmittedCountColumn", "scoreBandChart"
        )) {
            assertTrue("Missing fx:id " + required, ids.containsKey(required));
        }
        for (Map.Entry<String, Element> entry : ids.entrySet()) {
            Field field = ReportsPage.class.getDeclaredField(entry.getKey());
            assertTrue(field.isAnnotationPresent(FXML.class));
            assertTrue(
                    "Incompatible field " + entry.getKey(),
                    field.getType().isAssignableFrom(fxmlType(entry.getValue().getTagName()))
            );
        }

        Set<String> actions = new HashSet<>();
        collectActions(document.getDocumentElement(), actions);
        assertEquals(Set.of("handleLoadReport", "handleRefresh", "handleBack"), actions);
        for (String action : actions) {
            Method method = ReportsPage.class.getDeclaredMethod(action);
            assertTrue(method.isAnnotationPresent(FXML.class));
        }

        Element table = ids.get("executionTable");
        assertFalse(table.hasAttribute("placeholder"));
        assertEquals(1, table.getElementsByTagName("placeholder").getLength());
        assertEquals(1, ((Element) table.getElementsByTagName("placeholder").item(0))
                .getElementsByTagName("Label").getLength());
    }

    @Test
    public void fxmlResourceAndChartArePresentWithoutNetworkingOrSensitiveContent()
            throws Exception {
        URL resource = ReportsPage.class.getResource(
                "/hsts/client/boundary/reports-page.fxml"
        );
        assertNotNull(resource);
        String fxml = Files.readString(FXML);
        assertTrue(fxml.contains("<BarChart fx:id=\"scoreBandChart\""));
        assertTrue(fxml.contains("<CategoryAxis label=\"Score band\""));
        assertTrue(fxml.contains("<NumberAxis label=\"Published submissions\""));
        assertTrue(fxml.contains("-fx-text-fill: #111827"));
        for (String forbidden : List.of(
                "new Client", "sendRequest", "RequestType", "repository",
                "correct option", "selected option", "password", "hash",
                "feedback", "adjustment", "export"
        )) {
            assertFalse("Forbidden FXML token: " + forbidden,
                    fxml.toLowerCase().contains(forbidden.toLowerCase()));
        }
    }

    private static void assertValidation(String text, String message) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ReportsPage.parseTargetId(text)
        );
        assertEquals(message, exception.getMessage());
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
            case "TextField" -> javafx.scene.control.TextField.class;
            case "ProgressIndicator" -> javafx.scene.control.ProgressIndicator.class;
            case "TableView" -> javafx.scene.control.TableView.class;
            case "TableColumn" -> javafx.scene.control.TableColumn.class;
            case "HBox" -> javafx.scene.layout.HBox.class;
            case "BarChart" -> javafx.scene.chart.BarChart.class;
            default -> throw new AssertionError("Unexpected fx:id element " + tagName);
        };
    }
}
