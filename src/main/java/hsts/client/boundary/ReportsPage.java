package hsts.client.boundary;

import hsts.client.control.ReportClientController;
import hsts.client.net.Client;
import hsts.common.ExamStatisticsDTO;
import hsts.common.LoginResult;
import hsts.common.ReportSummaryDTO;
import hsts.common.ScoreBandDTO;
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

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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

    @FXML private Label roleContextLabel;
    @FXML private HBox principalControls;
    @FXML private ComboBox<String> comparisonModeComboBox;
    @FXML private TextField targetIdField;
    @FXML private Button loadButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
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
                "Teacher", "Course", "Student"
        ));
        comparisonModeComboBox.getSelectionModel().selectFirst();
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
        targetIdField.setDisable(!principal);
        comparisonModeComboBox.setDisable(!principal);
        setError("");
        setStatus(principal
                ? "Choose a comparison type and enter a positive target ID."
                : "Loading your authored exam report...");
        updateControlState();

        if (!principal && !automaticLoadStarted) {
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
        final int targetId;
        try {
            targetId = parseTargetId(targetIdField.getText());
        } catch (IllegalArgumentException exception) {
            setError(exception.getMessage());
            return;
        }

        String mode = comparisonModeComboBox.getValue();
        CompletableFuture<ReportSummaryDTO> request = switch (mode) {
            case "Teacher" -> reportClientController.getTeacherExamsReport(targetId);
            case "Course" -> reportClientController.getCourseExamsReport(targetId);
            case "Student" -> reportClientController.getStudentExamsReport(targetId);
            default -> throw new IllegalStateException("Report comparison type is required");
        };
        loadReport(request);
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
        if (comparisonModeComboBox != null) {
            comparisonModeComboBox.setDisable(!principal || loading);
        }
        if (targetIdField != null) targetIdField.setDisable(!principal || loading);
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
