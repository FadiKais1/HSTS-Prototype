package hsts.client.boundary;

import hsts.client.control.ReportClientController;
import hsts.client.net.Client;
import hsts.common.ExamStatisticsDTO;
import hsts.common.LoginResult;
import hsts.common.ReportSummaryDTO;
import hsts.common.ReportTargetOptionDTO;
import hsts.common.ReportTargetsDTO;
import hsts.common.ReportExportPayload;
import hsts.common.ReportExportResult;
import hsts.common.ScoreBandDTO;
import hsts.common.type.ReportExportFormat;
import hsts.common.type.UserRole;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class ReportsPage {
    private static final String CONFIGURATION_ERROR = "Reports page is not configured";
    private static final String SAFE_LOAD_ERROR = "Unable to load report";
    private static final String INVALID_RESPONSE = "Invalid report response from server";
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObservableList<ExamStatisticsDTO> executions =
            FXCollections.observableArrayList();

    private Stage stage;
    private Client client;
    private LoginResult loginResult;
    private Runnable backHandler;
    private ReportClientController reportClientController;
    private ReportSummaryDTO currentReport;
    private long requestGeneration;
    private boolean loading;
    private boolean disposed;
    private boolean automaticLoadStarted;
    private boolean exporting;
    private ReportExportResult pendingExport;
    private ReportExportFormat pendingExportFormat;

    @FXML private Label roleContextLabel;
    @FXML private HBox principalControls;
    @FXML private ComboBox<String> comparisonModeComboBox;
    @FXML private ComboBox<ReportTargetOptionDTO> targetComboBox;

    /** Every target the Principal may report on, loaded once when the page opens. */
    private ReportTargetsDTO reportTargets;
    @FXML private Button loadButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private Button exportPdfButton;
    @FXML private Button exportExcelButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;
    @FXML private Label errorLabel;
    @FXML private Label reportTitleLabel;
    @FXML private Label targetNameLabel;
    @FXML private Label generatedAtLabel;
    @FXML private Label executionCountLabel;
    @FXML private TableView<ExamStatisticsDTO> executionTable;
    @FXML private TableColumn<ExamStatisticsDTO, String> executionCodeColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> examTitleColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> courseNameColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> openingTimeColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> closingTimeColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> publishedCountColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> averageScoreColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> medianScoreColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> startedCountColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> submittedCountColumn;
    @FXML private TableColumn<ExamStatisticsDTO, String> autoSubmittedCountColumn;
    @FXML private VBox detailContainer;
    @FXML private Label detailPromptLabel;
    @FXML private GridPane detailContent;
    @FXML private Label detailCodeCaption;
    @FXML private Label detailExamCaption;
    @FXML private Label detailVersionCaption;
    @FXML private Label detailCourseCaption;
    @FXML private Label detailOpeningCaption;
    @FXML private Label detailClosingCaption;
    @FXML private Label detailPublishedCaption;
    @FXML private Label detailAverageCaption;
    @FXML private Label detailMedianCaption;
    @FXML private Label detailStartedCaption;
    @FXML private Label detailSubmittedCaption;
    @FXML private Label detailAutoSubmittedCaption;
    @FXML private Label detailExecutionCodeLabel;
    @FXML private Label detailExamTitleLabel;
    @FXML private Label detailVersionLabel;
    @FXML private Label detailCourseLabel;
    @FXML private Label detailOpeningLabel;
    @FXML private Label detailClosingLabel;
    @FXML private Label detailPublishedLabel;
    @FXML private Label detailAverageLabel;
    @FXML private Label detailMedianLabel;
    @FXML private Label detailStartedLabel;
    @FXML private Label detailSubmittedLabel;
    @FXML private Label detailAutoSubmittedLabel;
    @FXML private BarChart<String, Number> scoreBandChart;
    @FXML private CategoryAxis scoreBandAxis;
    @FXML private NumberAxis submissionCountAxis;

    @FXML
    private void initialize() {
        comparisonModeComboBox.setItems(FXCollections.observableArrayList(
                "Teacher", "Course", "Student", "Execution"
        ));
        comparisonModeComboBox.getSelectionModel().selectFirst();
        // Changing the scope refills the target picker from the loaded lists.
        comparisonModeComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> populateTargets(selected)
        );
        configureTable();
        executionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        executionTable.setItems(executions);
        executionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> showExecution(selected)
        );
        clearReportDisplay();
        setError("");
        setStatus(CONFIGURATION_ERROR);
        updateControlState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable backHandler) {
        this.stage = Objects.requireNonNull(stage, CONFIGURATION_ERROR);
        this.client = Objects.requireNonNull(client, CONFIGURATION_ERROR);
        this.loginResult = Objects.requireNonNull(loginResult, CONFIGURATION_ERROR);
        this.backHandler = Objects.requireNonNull(backHandler, CONFIGURATION_ERROR);
        this.reportClientController = new ReportClientController(this.client);
        this.disposed = false;
        this.requestGeneration++;

        UserRole role = loginResult.getRole();
        if (role != UserRole.TEACHER
                && role != UserRole.COORDINATOR
                && role != UserRole.PRINCIPAL) {
            throw new IllegalArgumentException(CONFIGURATION_ERROR);
        }

        roleContextLabel.setText(
                loginResult.getFullName() + " · " + role.name()
        );
        boolean principal = role == UserRole.PRINCIPAL;
        principalControls.setManaged(principal);
        principalControls.setVisible(principal);
        targetComboBox.setDisable(!principal);
        comparisonModeComboBox.setDisable(!principal);
        setError("");
        setStatus(principal
                ? "Choose a report type and target, then press Load Report."
                : "Loading your authored exam report...");
        updateControlState();

        if (principal) {
            loadReportTargets();
        } else if (!automaticLoadStarted) {
            automaticLoadStarted = true;
            loadReport(reportClientController.getMyAuthoredExamsReport());
        }
    }

    @FXML
    private void handleLoadReport() {
        if (!isConfigured()) {
            setError(CONFIGURATION_ERROR);
            return;
        }
        if (loginResult.getRole() != UserRole.PRINCIPAL) {
            refreshCurrentScope();
            return;
        }
        loadPrincipalComparison();
    }

    @FXML
    private void handleRefresh() {
        if (!isConfigured()) {
            setError(CONFIGURATION_ERROR);
            return;
        }
        refreshCurrentScope();
    }

    @FXML
    private void handleExportPdf() {
        exportCurrentReport(ReportExportFormat.PDF);
    }

    @FXML
    private void handleExportExcel() {
        exportCurrentReport(ReportExportFormat.XLSX);
    }

    private void exportCurrentReport(ReportExportFormat format) {
        if (!isConfigured() || currentReport == null || exporting) return;
        if (pendingExport != null && pendingExportFormat == format) {
            chooseExportDestination(pendingExport, format);
            return;
        }
        exporting = true;
        setError("");
        setStatus("Generating report export...");
        updateControlState();
        ReportExportPayload payload = new ReportExportPayload(
                currentReport.getReportType(),
                loginResult.getRole() == UserRole.PRINCIPAL
                        ? currentReport.getTargetId()
                        : currentReport.getReportType()
                        == hsts.common.type.ReportType.EXAM_EXECUTION
                        ? currentReport.getTargetId()
                        : null,
                format
        );
        reportClientController.exportReport(payload).whenComplete((result, failure) ->
                Platform.runLater(() -> finishExport(result, format, failure)));
    }

    private void finishExport(ReportExportResult result, ReportExportFormat format,
                              Throwable failure) {
        if (disposed) return;
        if (failure != null || result == null) {
            exporting = false;
            setError(failure == null ? "Unable to export report" : cleanError(failure));
            setStatus("");
            updateControlState();
            return;
        }
        pendingExport = result;
        pendingExportFormat = format;
        chooseExportDestination(result, format);
    }

    private void chooseExportDestination(ReportExportResult result,
                                         ReportExportFormat format) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save report export");
        FileChooser.ExtensionFilter filter = extensionFilter(format);
        chooser.getExtensionFilters().setAll(filter);
        chooser.setSelectedExtensionFilter(filter);
        chooser.setInitialFileName(normalizeFilename(
                result.getSuggestedFilename(),
                format
        ));
        java.io.File selected = chooser.showSaveDialog(stage);
        if (selected == null) {
            exporting = false;
            setStatus("Export canceled.");
            updateControlState();
            return;
        }
        Path destination = normalizeDestination(selected.toPath(), format);
        exporting = true;
        setError("");
        setStatus("Saving report export...");
        updateControlState();
        CompletableFuture.runAsync(() -> {
            try {
                writeAtomically(destination, result.getBytes());
            } catch (IOException | SecurityException exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).whenComplete((unused, writeFailure) -> Platform.runLater(() -> {
            if (disposed) return;
            exporting = false;
            if (writeFailure != null) {
                setError(saveFailureMessage(writeFailure));
                setStatus("");
            } else {
                pendingExport = null;
                pendingExportFormat = null;
                setError("");
                setStatus("Report exported successfully.");
            }
            updateControlState();
        }));
    }

    @FXML
    private void handleBack() {
        if (!isConfigured()) {
            setError(CONFIGURATION_ERROR);
            return;
        }
        disposed = true;
        requestGeneration++;
        loading = false;
        updateControlState();
        try {
            backHandler.run();
        } catch (RuntimeException exception) {
            disposed = false;
            setError("Unable to return to dashboard");
            updateControlState();
        }
    }

    private void refreshCurrentScope() {
        if (loading) {
            return;
        }
        if (loginResult.getRole() == UserRole.PRINCIPAL) {
            loadPrincipalComparison();
        } else {
            loadReport(reportClientController.getMyAuthoredExamsReport());
        }
    }

    private void loadPrincipalComparison() {
        if (loading) {
            return;
        }
        ReportTargetOptionDTO target = targetComboBox.getValue();
        if (target == null) {
            setError("Choose a target for the report.");
            return;
        }
        int targetId = target.getTargetId();

        String mode = comparisonModeComboBox.getValue();
        CompletableFuture<ReportSummaryDTO> request = switch (mode) {
            case "Teacher" -> reportClientController.getTeacherExamsReport(targetId);
            case "Course" -> reportClientController.getCourseExamsReport(targetId);
            case "Student" -> reportClientController.getStudentExamsReport(targetId);
            case "Execution" -> reportClientController.getExamExecutionReport(targetId);
            default -> throw new IllegalStateException("Report comparison type is required");
        };
        loadReport(request);
    }

    /**
     * Loads every target the Principal may report on, once, so the picker can
     * offer real names instead of asking for a numeric id.
     */
    private void loadReportTargets() {
        if (reportClientController == null) {
            return;
        }
        reportClientController.getReportTargets().whenComplete((targets, error) ->
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
                    if (error != null) {
                        setError("Unable to load report targets.");
                        return;
                    }
                    reportTargets = targets;
                    populateTargets(comparisonModeComboBox.getValue());
                })
        );
    }

    private void populateTargets(String mode) {
        if (reportTargets == null || mode == null) {
            return;
        }
        List<ReportTargetOptionDTO> options = switch (mode) {
            case "Teacher" -> reportTargets.getTeachers();
            case "Course" -> reportTargets.getCourses();
            case "Student" -> reportTargets.getStudents();
            case "Execution" -> reportTargets.getExecutions();
            default -> List.of();
        };
        targetComboBox.setItems(FXCollections.observableArrayList(options));
        if (!options.isEmpty()) {
            targetComboBox.getSelectionModel().selectFirst();
        }
    }

    private void loadReport(CompletableFuture<ReportSummaryDTO> request) {
        if (loading || disposed) {
            return;
        }
        long generation = ++requestGeneration;
        loading = true;
        setError("");
        setStatus("Loading report...");
        updateControlState();
        request.whenComplete((report, failure) -> Platform.runLater(() -> {
            if (isStale(disposed, generation, requestGeneration)) {
                return;
            }
            loading = false;
            if (failure != null) {
                setError(cleanError(failure));
                setStatus("");
                updateControlState();
                return;
            }
            if (report == null) {
                setError(INVALID_RESPONSE);
                setStatus("");
                updateControlState();
                return;
            }
            showReport(report);
            updateControlState();
        }));
    }

    private void showReport(ReportSummaryDTO report) {
        pendingExport = null;
        pendingExportFormat = null;
        currentReport = report;
        reportTitleLabel.setText(report.getTitle());
        targetNameLabel.setText(report.getTargetDisplayName() == null
                ? "" : report.getTargetDisplayName());
        generatedAtLabel.setText(DATE_TIME_FORMAT.format(report.getGeneratedAt()));
        executionCountLabel.setText(Integer.toString(report.getExamStatistics().size()));
        executions.setAll(report.getExamStatistics());
        executionTable.getSelectionModel().clearSelection();
        clearExecutionDetail();
        setError("");
        if (executions.isEmpty()) {
            setStatus("No exam executions are available for this report.");
        } else {
            setStatus("Report loaded. Select an execution for details.");
        }
    }

    private void showExecution(ExamStatisticsDTO execution) {
        clearExecutionDetail();
        if (execution == null) {
            return;
        }
        detailPromptLabel.setVisible(false);
        detailPromptLabel.setManaged(false);
        detailContent.setVisible(true);
        detailContent.setManaged(true);
        detailExecutionCodeLabel.setText(execution.getExamCode());
        detailExamTitleLabel.setText(execution.getExamTitle());
        detailVersionLabel.setText(Integer.toString(execution.getExamVersionNo()));
        detailCourseLabel.setText(execution.getCourseName());
        detailOpeningLabel.setText(DATE_TIME_FORMAT.format(execution.getOpeningTime()));
        detailClosingLabel.setText(DATE_TIME_FORMAT.format(execution.getClosingTime()));
        detailPublishedLabel.setText(
                Integer.toString(execution.getPublishedSubmissionCount())
        );
        detailAverageLabel.setText(formatScore(execution.getAverageScore()));
        detailMedianLabel.setText(formatScore(execution.getMedianScore()));
        detailStartedLabel.setText(Integer.toString(execution.getStartedSubmissionCount()));
        detailSubmittedLabel.setText(
                Integer.toString(execution.getSubmittedSubmissionCount())
        );
        detailAutoSubmittedLabel.setText(
                Integer.toString(execution.getAutoSubmittedSubmissionCount())
        );

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Published submissions");
        for (ScoreBandDTO band : execution.getScoreBands()) {
            series.getData().add(new XYChart.Data<>(
                    bandLabel(band),
                    band.getSubmissionCount()
            ));
        }
        int maximumCount = execution.getScoreBands().stream()
                .mapToInt(ScoreBandDTO::getSubmissionCount)
                .max()
                .orElse(0);
        int upperBound = Math.max(1, maximumCount);
        submissionCountAxis.setLowerBound(0);
        submissionCountAxis.setUpperBound(upperBound);
        submissionCountAxis.setTickUnit(Math.max(1, Math.ceil(upperBound / 5.0)));
        scoreBandChart.getData().setAll(series);
    }

    private void clearReportDisplay() {
        currentReport = null;
        executions.clear();
        reportTitleLabel.setText("");
        targetNameLabel.setText("");
        generatedAtLabel.setText("");
        executionCountLabel.setText("0");
        clearExecutionDetail();
    }

    private void clearExecutionDetail() {
        detailContainer.setVisible(true);
        detailContainer.setManaged(true);
        detailPromptLabel.setVisible(true);
        detailPromptLabel.setManaged(true);
        detailContent.setVisible(false);
        detailContent.setManaged(false);
        for (Label label : List.of(
                detailExecutionCodeLabel, detailExamTitleLabel, detailVersionLabel,
                detailCourseLabel, detailOpeningLabel, detailClosingLabel,
                detailPublishedLabel, detailAverageLabel, detailMedianLabel,
                detailStartedLabel, detailSubmittedLabel,
                detailAutoSubmittedLabel
        )) {
            label.setText("-");
        }
        scoreBandChart.getData().clear();
        submissionCountAxis.setLowerBound(0);
        submissionCountAxis.setUpperBound(1);
        submissionCountAxis.setTickUnit(1);
    }

    private void configureTable() {
        executionCodeColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getExamCode()));
        examTitleColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getExamTitle()));
        courseNameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getCourseName()));
        openingTimeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                DATE_TIME_FORMAT.format(cell.getValue().getOpeningTime())
        ));
        closingTimeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                DATE_TIME_FORMAT.format(cell.getValue().getClosingTime())
        ));
        publishedCountColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Integer.toString(cell.getValue().getPublishedSubmissionCount())
        ));
        averageScoreColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                formatScore(cell.getValue().getAverageScore())
        ));
        medianScoreColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                formatScore(cell.getValue().getMedianScore())
        ));
        startedCountColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Integer.toString(cell.getValue().getStartedSubmissionCount())
        ));
        submittedCountColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Integer.toString(cell.getValue().getSubmittedSubmissionCount())
        ));
        autoSubmittedCountColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Integer.toString(cell.getValue().getAutoSubmittedSubmissionCount())
        ));
    }

    private void updateControlState() {
        boolean configured = isConfigured();
        boolean principal = configured && loginResult.getRole() == UserRole.PRINCIPAL;
        if (loadButton != null) loadButton.setDisable(!principal || loading);
        if (refreshButton != null) refreshButton.setDisable(!configured || loading);
        boolean exportDisabled = !configured || loading || exporting
                || currentReport == null;
        if (exportPdfButton != null) exportPdfButton.setDisable(exportDisabled);
        if (exportExcelButton != null) exportExcelButton.setDisable(exportDisabled);
        if (comparisonModeComboBox != null) {
            comparisonModeComboBox.setDisable(!principal || loading);
        }
        if (targetComboBox != null) targetComboBox.setDisable(!principal || loading);
        if (loadingIndicator != null) {
            loadingIndicator.setManaged(loading);
            loadingIndicator.setVisible(loading);
        }
    }

    private boolean isConfigured() {
        return !disposed && stage != null && client != null && loginResult != null
                && backHandler != null && reportClientController != null;
    }

    static int parseTargetId(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Target ID is required");
        }
        try {
            int value = Integer.parseInt(text.trim());
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "Target ID must be a positive number"
                );
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Target ID must be a positive number");
        }
    }

    static String formatScore(BigDecimal value) {
        return value == null ? "N/A" : value.toPlainString();
    }

    static String bandLabel(ScoreBandDTO band) {
        return band.getLowerBoundInclusive() + "–" + band.getUpperBoundInclusive();
    }

    static List<String> bandLabels(List<ScoreBandDTO> bands) {
        List<String> labels = new ArrayList<>(bands.size());
        for (ScoreBandDTO band : bands) {
            labels.add(bandLabel(band));
        }
        return List.copyOf(labels);
    }

    static boolean isStale(boolean disposed, long responseGeneration,
                           long currentGeneration) {
        return disposed || responseGeneration != currentGeneration;
    }

    static FileChooser.ExtensionFilter extensionFilter(ReportExportFormat format) {
        return switch (Objects.requireNonNull(format, "format")) {
            case PDF -> new FileChooser.ExtensionFilter(
                    "PDF files (*.pdf)", "*.pdf"
            );
            case XLSX -> new FileChooser.ExtensionFilter(
                    "Excel workbooks (*.xlsx)", "*.xlsx"
            );
        };
    }

    static String normalizeFilename(String suggested,
                                    ReportExportFormat format) {
        String extension = format == ReportExportFormat.PDF ? ".pdf" : ".xlsx";
        String name = suggested == null ? "" : suggested.trim();
        if (name.isBlank()) name = "hsts-report";
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        while (lower.endsWith(".pdf") || lower.endsWith(".xlsx")) {
            int separator = name.lastIndexOf('.');
            name = name.substring(0, separator);
            lower = name.toLowerCase(java.util.Locale.ROOT);
        }
        return name + extension;
    }

    static Path normalizeDestination(Path selected,
                                     ReportExportFormat format) {
        Objects.requireNonNull(selected, "selected");
        Path filename = selected.getFileName();
        String normalized = normalizeFilename(
                filename == null ? null : filename.toString(),
                format
        );
        Path parent = selected.getParent();
        return parent == null ? Path.of(normalized) : parent.resolve(normalized);
    }

    static void writeAtomically(Path destination, byte[] bytes) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(bytes, "bytes");
        Path absolute = destination.toAbsolutePath();
        Path directory = absolute.getParent();
        if (directory == null) {
            throw new IOException("Export destination has no parent directory");
        }
        Path temporary = null;
        Throwable failure = null;
        try {
            temporary = Files.createTempFile(
                    directory,
                    "." + absolute.getFileName() + ".",
                    ".tmp"
            );
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            temporary = null;
        } catch (IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | RuntimeException cleanupFailure) {
                    if (failure != null) {
                        failure.addSuppressed(cleanupFailure);
                    } else if (cleanupFailure instanceof IOException ioFailure) {
                        throw ioFailure;
                    } else {
                        throw cleanupFailure;
                    }
                }
            }
        }
    }

    static String saveFailureMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current instanceof AccessDeniedException
                || current instanceof SecurityException) {
            return "The selected folder does not allow this application to save "
                    + "files. Choose another folder or allow Java through Windows "
                    + "Controlled Folder Access.";
        }
        return "Unable to save report export. Choose another folder.";
    }

    private String cleanError(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? SAFE_LOAD_ERROR : message;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private void setError(String message) {
        if (errorLabel != null) {
            boolean visible = message != null && !message.isBlank();
            errorLabel.setText(message == null ? "" : message);
            errorLabel.setManaged(visible);
            errorLabel.setVisible(visible);
        }
    }
}
