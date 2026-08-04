package hsts.client.boundary;

import hsts.common.ExamStatisticsDTO;
import hsts.common.ReportSummaryDTO;
import hsts.common.ScoreBandDTO;
import hsts.common.type.ReportType;
import hsts.common.type.ReportExportFormat;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    public void realFxmlSelectionRendersReadableDetailsAndAllBands() throws Exception {
        String javaExecutable = Path.of(
                System.getProperty("java.home"), "bin", "java.exe"
        ).toString();
        Process process = new ProcessBuilder(
                javaExecutable,
                "-cp",
                System.getProperty("java.class.path"),
                RenderingHarness.class.getName()
        ).redirectErrorStream(true).start();

        assertTrue("JavaFX rendering harness timed out",
                process.waitFor(30, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(output, 0, process.exitValue());
    }

    @Test
    public void targetValidationIsExactAndOccursBeforeControllerCalls() throws Exception {
        assertValidation(null, "Target ID is required");
        assertValidation("  ", "Target ID is required");
        assertValidation("abc", "Target ID must be a positive number");
        assertValidation("0", "Target ID must be a positive number");
        assertValidation("-8", "Target ID must be a positive number");
        assertEquals(42, ReportsPage.parseTargetId(" 42 "));

        String source = Files.readString(SOURCE);
        // The Principal now picks a target by name, so the guard is a null check
        // on the selection rather than parsing hand-typed text. It must still run
        // before any controller call.
        int guard = source.indexOf("ReportTargetOptionDTO target = targetComboBox.getValue()");
        int teacher = source.indexOf("getTeacherExamsReport(targetId)");
        int course = source.indexOf("getCourseExamsReport(targetId)");
        int student = source.indexOf("getStudentExamsReport(targetId)");
        int execution = source.indexOf("getExamExecutionReport(targetId)");
        assertTrue(guard >= 0 && guard < teacher && guard < course
                && guard < student && guard < execution);
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
    public void exportFiltersFilenamesAndPermissionMessageAreFormatSafe() {
        javafx.stage.FileChooser.ExtensionFilter pdf =
                ReportsPage.extensionFilter(ReportExportFormat.PDF);
        javafx.stage.FileChooser.ExtensionFilter excel =
                ReportsPage.extensionFilter(ReportExportFormat.XLSX);
        assertEquals("PDF files (*.pdf)", pdf.getDescription());
        assertEquals(List.of("*.pdf"), pdf.getExtensions());
        assertEquals("Excel workbooks (*.xlsx)", excel.getDescription());
        assertEquals(List.of("*.xlsx"), excel.getExtensions());
        assertEquals("report.pdf", ReportsPage.normalizeFilename(
                "report.xlsx", ReportExportFormat.PDF
        ));
        assertEquals("report.xlsx", ReportsPage.normalizeFilename(
                "report.pdf.xlsx", ReportExportFormat.XLSX
        ));
        assertEquals("report.pdf", ReportsPage.normalizeFilename(
                "report", ReportExportFormat.PDF
        ));
        assertEquals(
                "The selected folder does not allow this application to save files. "
                        + "Choose another folder or allow Java through Windows "
                        + "Controlled Folder Access.",
                ReportsPage.saveFailureMessage(
                        new java.util.concurrent.CompletionException(
                                new AccessDeniedException("Desktop")
                        )
                )
        );
    }

    @Test
    public void exportWritesAtomicallyAndCleansTemporaryFileOnFailure()
            throws Exception {
        Path directory = Files.createTempDirectory("hsts-report-save-");
        try {
            Path destination = directory.resolve("report.pdf");
            Files.writeString(destination, "old");
            byte[] expected = "%PDF-safe".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

            ReportsPage.writeAtomically(destination, expected);

            assertTrue(java.util.Arrays.equals(expected, Files.readAllBytes(destination)));
            Path occupied = Files.createDirectory(directory.resolve("occupied.pdf"));
            Files.writeString(occupied.resolve("keep.txt"), "keep");
            assertThrows(Exception.class, () ->
                    ReportsPage.writeAtomically(occupied, expected));
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString()
                        .endsWith(".tmp")));
            }
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.io.IOException ignored) {
                        // Best-effort cleanup of test-only temporary files.
                    }
                });
            }
        }
    }

    @Test
    public void exportRetryReusesGeneratedBytesAndCancelWritesNothing() throws Exception {
        String source = Files.readString(SOURCE);
        String exportMethod = methodSource(source, "private void exportReport(");
        String chooserMethod = methodSource(source, "private void chooseExportDestination(");
        assertTrue(exportMethod.indexOf("pendingExport != null")
                < exportMethod.indexOf("exportReport(payload)"));
        assertTrue(chooserMethod.contains("if (selected == null)"));
        assertTrue(chooserMethod.indexOf("if (selected == null)")
                < chooserMethod.indexOf("writeAtomically("));
        assertTrue(chooserMethod.contains("CompletableFuture.runAsync("));
        assertFalse(chooserMethod.contains(".join("));
        assertFalse(chooserMethod.contains(".get("));
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
        // The execution report is now a fourth top-level type the Principal can
        // choose from the picker, alongside reaching it by clicking a row in the
        // Execution Statistics table. The route, service and export already
        // existed; only the interface withheld it.
        assertTrue(source.contains("getExamExecutionReport(targetId)"));
        for (String forbidden : List.of(
                "client.sendRequest", "new Client(", "client.close(",
                "client.connect(", "client.disconnect(", "hsts.common.Request",
                "hsts.common.Response", "hsts.server.", "studentId",
                "teacherFeedback", "adjustmentReason", "correctOption",
                "selectedOption", "password", "identityHash", "server.entity"
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
        String main = Files.readString(MAIN_CLIENT).replace("\r\n", "\n");

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
                "targetComboBox", "loadButton", "refreshButton", "backButton",
                "loadingIndicator", "statusLabel", "errorLabel", "reportTitleLabel",
                "targetNameLabel", "generatedAtLabel", "executionCountLabel",
                "executionTable", "executionCodeColumn", "examTitleColumn",
                "courseNameColumn", "openingTimeColumn", "closingTimeColumn",
                "publishedCountColumn", "averageScoreColumn", "medianScoreColumn",
                "startedCountColumn", "submittedCountColumn",
                "autoSubmittedCountColumn", "scoreBandChart",
                "detailContainer", "detailPromptLabel", "detailContent",
                "detailCodeCaption", "detailExamCaption", "detailVersionCaption",
                "detailCourseCaption", "detailOpeningCaption", "detailClosingCaption",
                "detailPublishedCaption", "detailAverageCaption", "detailMedianCaption",
                "detailStartedCaption", "detailSubmittedCaption",
                "detailAutoSubmittedCaption",
                "scoreBandAxis", "submissionCountAxis", "exportPdfButton",
                "exportExcelButton", "exportComparisonPdfButton",
                "exportComparisonExcelButton"
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
        assertEquals(Set.of(
                "handleLoadReport", "handleRefresh", "handleBack",
                "handleExportPdf", "handleExportExcel",
                "handleExportComparisonPdf", "handleExportComparisonExcel",
                // Drops the second report and returns to a single full width table.
                "handleClearComparison"
        ), actions);
        for (String action : actions) {
            Method method = ReportsPage.class.getDeclaredMethod(action);
            assertTrue(method.isAnnotationPresent(FXML.class));
        }

        // The Principal compares two targets side by side, so the split view and
        // its summary must exist and start hidden.
        for (String required : List.of(
                "comparisonTargetComboBox", "statisticsSplit", "comparisonContainer",
                "comparisonSummaryContainer", "comparisonTable", "comparisonSummaryLabel"
        )) {
            assertTrue("Missing comparison fx:id " + required, ids.containsKey(required));
        }
        assertEquals("false", ids.get("comparisonContainer").getAttribute("visible"));
        assertEquals("false", ids.get("comparisonSummaryContainer").getAttribute("visible"));
        assertEquals("false", ids.get("exportComparisonPdfButton")
                .getAttribute("visible"));
        assertEquals("false", ids.get("exportComparisonExcelButton")
                .getAttribute("visible"));

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
        assertTrue(fxml.contains("<CategoryAxis fx:id=\"scoreBandAxis\" label=\"Score band\""));
        assertTrue(fxml.contains("<NumberAxis fx:id=\"submissionCountAxis\" label=\"Published submissions\""));
        assertTrue(fxml.contains("-fx-text-fill: #111827"));
        for (String forbidden : List.of(
                "new Client", "sendRequest", "RequestType", "repository",
                "correct option", "selected option", "password", "hash",
                "feedback", "adjustment", "client.sendrequest", "java.io.file"
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
            case "VBox" -> javafx.scene.layout.VBox.class;
            case "GridPane" -> javafx.scene.layout.GridPane.class;
            case "BarChart" -> javafx.scene.chart.BarChart.class;
            case "CategoryAxis" -> javafx.scene.chart.CategoryAxis.class;
            case "NumberAxis" -> javafx.scene.chart.NumberAxis.class;
            default -> throw new AssertionError("Unexpected fx:id element " + tagName);
        };
    }

    private static String methodSource(String source, String methodStart) {
        int start = source.indexOf(methodStart);
        if (start < 0) throw new AssertionError("Method not found: " + methodStart);
        int openingBrace = source.indexOf('{', start);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') depth++;
            else if (character == '}' && --depth == 0) {
                return source.substring(start, index + 1);
            }
        }
        throw new AssertionError("Method not closed: " + methodStart);
    }

    public static final class RenderingHarness {
        private RenderingHarness() {
        }

        public static void main(String[] args) {
            Platform.startup(() -> {
                try {
                    verifyRendering();
                    System.exit(0);
                } catch (Throwable failure) {
                    failure.printStackTrace(System.err);
                    System.exit(1);
                }
            });
        }

        private static void verifyRendering() throws Exception {
            FXMLLoader loader = new FXMLLoader(ReportsPage.class.getResource(
                    "/hsts/client/boundary/reports-page.fxml"
            ));
            Parent root = loader.load();
            ReportsPage controller = loader.getController();
            Scene scene = new Scene(root);
            root.applyCss();
            root.layout();

            ExamStatisticsDTO populated = execution(
                    41, "RLYQ", new BigDecimal("100.00"),
                    new BigDecimal("100.00"), 1
            );
            invokeShowReport(controller, report(List.of(populated)));
            table(controller).getSelectionModel().selectFirst();
            root.applyCss();
            root.layout();

            assertDetail(controller, "detailExecutionCodeLabel", "RLYQ");
            assertDetail(controller, "detailExamTitleLabel", "Acceptance Exam");
            assertDetail(controller, "detailVersionLabel", "3");
            assertDetail(controller, "detailCourseLabel", "Software Engineering");
            assertDetail(controller, "detailOpeningLabel", "2026-07-28 09:00:00");
            assertDetail(controller, "detailClosingLabel", "2026-07-28 10:30:00");
            assertDetail(controller, "detailPublishedLabel", "1");
            assertDetail(controller, "detailAverageLabel", "100.00");
            assertDetail(controller, "detailMedianLabel", "100.00");
            assertDetail(controller, "detailStartedLabel", "3");
            assertDetail(controller, "detailSubmittedLabel", "1");
            assertDetail(controller, "detailAutoSubmittedLabel", "1");
            assertCaptionPair(controller, "detailCodeCaption", "Code",
                    "detailExecutionCodeLabel", 0);
            assertCaptionPair(controller, "detailExamCaption", "Exam",
                    "detailExamTitleLabel", 1);
            assertCaptionPair(controller, "detailVersionCaption", "Version",
                    "detailVersionLabel", 2);
            assertCaptionPair(controller, "detailCourseCaption", "Course",
                    "detailCourseLabel", 3);
            assertCaptionPair(controller, "detailOpeningCaption", "Opening",
                    "detailOpeningLabel", 4);
            assertCaptionPair(controller, "detailClosingCaption", "Closing",
                    "detailClosingLabel", 5);
            assertCaptionPair(controller, "detailPublishedCaption", "Published Results",
                    "detailPublishedLabel", 6);
            assertCaptionPair(controller, "detailAverageCaption", "Average",
                    "detailAverageLabel", 7);
            assertCaptionPair(controller, "detailMedianCaption", "Median",
                    "detailMedianLabel", 8);
            assertCaptionPair(controller, "detailStartedCaption", "Started",
                    "detailStartedLabel", 9);
            assertCaptionPair(controller, "detailSubmittedCaption", "Submitted",
                    "detailSubmittedLabel", 10);
            assertCaptionPair(controller, "detailAutoSubmittedCaption", "Auto-submitted",
                    "detailAutoSubmittedLabel", 11);

            VBox container = field(controller, "detailContainer", VBox.class);
            GridPane content = field(controller, "detailContent", GridPane.class);
            Label prompt = field(controller, "detailPromptLabel", Label.class);
            check(container.isVisible() && container.isManaged(), "detail container hidden");
            check(content.isVisible() && content.isManaged(), "detail content hidden");
            check(!prompt.isVisible() && !prompt.isManaged(), "prompt remained visible");

            @SuppressWarnings("unchecked")
            javafx.scene.chart.BarChart<String, Number> chart = field(
                    controller, "scoreBandChart", javafx.scene.chart.BarChart.class
            );
            check(chart.getData().size() == 1, "chart series missing");
            List<XYChart.Data<String, Number>> data = chart.getData().get(0).getData();
            check(data.size() == 10, "chart must retain ten bands");
            List<String> expected = List.of(
                    "0\u20139", "10\u201319", "20\u201329", "30\u201339", "40\u201349",
                    "50\u201359", "60\u201369", "70\u201379", "80\u201389", "90\u2013100"
            );
            for (int index = 0; index < 10; index++) {
                check(expected.get(index).equals(data.get(index).getXValue()),
                        "wrong band order at " + index);
                check(data.get(index).getYValue().intValue() == (index == 9 ? 1 : 0),
                        "wrong band count at " + index);
            }

            CategoryAxis categoryAxis = field(controller, "scoreBandAxis", CategoryAxis.class);
            NumberAxis numberAxis = field(controller, "submissionCountAxis", NumberAxis.class);
            assertReadableAxis(categoryAxis, categoryAxis.getTickLabelFill());
            assertReadableAxis(numberAxis, numberAxis.getTickLabelFill());
            check(numberAxis.getUpperBound() == 1.0 && numberAxis.getTickUnit() == 1.0,
                    "single-result numeric scale is not useful");

            invokeShowReport(controller, report(List.of()));
            check(chart.getData().isEmpty(), "old chart survived report change");
            check(prompt.isVisible() && prompt.isManaged(), "neutral prompt missing");
            check(!content.isVisible() && !content.isManaged(), "stale detail remained visible");
            check("-".equals(field(controller, "detailExecutionCodeLabel", Label.class).getText()),
                    "stale detail text remained");

            ExamStatisticsDTO empty = execution(42, "ZERO", null, null, 0);
            invokeShowReport(controller, report(List.of(empty)));
            table(controller).getSelectionModel().selectFirst();
            root.applyCss();
            root.layout();
            assertDetail(controller, "detailAverageLabel", "N/A");
            assertDetail(controller, "detailMedianLabel", "N/A");
            check(chart.getData().get(0).getData().stream()
                    .allMatch(point -> point.getYValue().intValue() == 0),
                    "zero-result chart contains a nonzero band");
            check(scene.getRoot() == root, "scene graph changed unexpectedly");
        }

        private static void assertReadableAxis(javafx.scene.chart.Axis<?> axis,
                                               javafx.scene.paint.Paint fill) {
            check(axis.isVisible() && axis.isManaged() && axis.getOpacity() == 1.0,
                    "axis hidden or transparent");
            check(fill instanceof Color, "axis tick fill is not a color");
            Color color = (Color) fill;
            check(color.getOpacity() == 1.0 && color.getBrightness() < 0.65,
                    "axis tick labels are not dark and opaque");
        }

        private static void assertDetail(ReportsPage controller, String name, String text)
                throws Exception {
            Label label = field(controller, name, Label.class);
            check(text.equals(label.getText()), name + " text mismatch: " + label.getText());
            check(label.isVisible() && label.isManaged() && label.getOpacity() == 1.0,
                    name + " hidden or transparent");
            check(label.getTextFill() instanceof Color, name + " fill is not a color");
            Color color = (Color) label.getTextFill();
            check(color.getOpacity() == 1.0 && color.getBrightness() < 0.65,
                    name + " is not dark and opaque");
        }

        private static void assertCaptionPair(ReportsPage controller,
                                              String captionName,
                                              String captionText,
                                              String valueName,
                                              int row) throws Exception {
            Label caption = field(controller, captionName, Label.class);
            Label value = field(controller, valueName, Label.class);
            check(captionText.equals(caption.getText()),
                    captionName + " text mismatch: " + caption.getText());
            check(caption.isVisible() && caption.isManaged() && caption.getOpacity() == 1.0,
                    captionName + " hidden or transparent");
            check(caption.getTextFill() instanceof Color,
                    captionName + " fill is not a color");
            Color color = (Color) caption.getTextFill();
            check(color.getOpacity() == 1.0 && color.getBrightness() < 0.65,
                    captionName + " is not dark and opaque");
            check(Integer.valueOf(row).equals(GridPane.getRowIndex(caption))
                            && Integer.valueOf(row).equals(GridPane.getRowIndex(value)),
                    captionName + " is not in the same row as " + valueName);
            check(Integer.valueOf(0).equals(GridPane.getColumnIndex(caption))
                            && Integer.valueOf(1).equals(GridPane.getColumnIndex(value)),
                    captionName + " is not adjacent to " + valueName);
        }

        private static ReportSummaryDTO report(List<ExamStatisticsDTO> executions) {
            return new ReportSummaryDTO(
                    ReportType.TEACHER_EXAMS, "Authored exams",
                    LocalDateTime.of(2026, 7, 28, 12, 0), 1002,
                    "Development Teacher", executions
            );
        }

        private static ExamStatisticsDTO execution(int id, String code,
                                                    BigDecimal average,
                                                    BigDecimal median,
                                                    int published) {
            List<ScoreBandDTO> bands = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                bands.add(new ScoreBandDTO(
                        index * 10, index == 9 ? 100 : index * 10 + 9,
                        published == 1 && index == 9 ? 1 : 0
                ));
            }
            return new ExamStatisticsDTO(
                    id, 7, 3, code, "Acceptance Exam", 5,
                    "Software Engineering", LocalDateTime.of(2026, 7, 28, 9, 0),
                    LocalDateTime.of(2026, 7, 28, 10, 30), published,
                    average, median, bands, 3, 1, 1
            );
        }

        private static void invokeShowReport(ReportsPage controller,
                                             ReportSummaryDTO report) throws Exception {
            Method method = ReportsPage.class.getDeclaredMethod(
                    "showReport", ReportSummaryDTO.class
            );
            method.setAccessible(true);
            method.invoke(controller, report);
        }

        @SuppressWarnings("unchecked")
        private static TableView<ExamStatisticsDTO> table(ReportsPage controller)
                throws Exception {
            return field(controller, "executionTable", TableView.class);
        }

        private static <T> T field(ReportsPage controller, String name, Class<T> type)
                throws Exception {
            Field field = ReportsPage.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(controller));
        }

        private static void check(boolean condition, String message) {
            if (!condition) {
                throw new AssertionError(message);
            }
        }
    }
}
