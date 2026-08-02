package hsts.client.boundary;

import hsts.client.control.ExamClientController;
import hsts.client.control.ExamExecutionClientController;
import hsts.client.net.Client;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.ExtendExecutionTimePayload;
import hsts.common.ExamSummaryDTO;
import hsts.common.LoginResult;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.type.ExamStatus;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.SubmissionStatus;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;

public class ExamSchedulingPage {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObservableList<ExamSummaryDTO> approvedExams =
            FXCollections.observableArrayList();
    private final ObservableList<ExamExecutionSummaryDTO> executions =
            FXCollections.observableArrayList();
    private final ObservableList<ExecutionSubmissionSummaryDTO> submissions =
            FXCollections.observableArrayList();

    private Stage stage;
    private Client client;
    private LoginResult loginResult;
    private Runnable backHandler;
    private ExamClientController examClientController;
    private ExamExecutionClientController executionClientController;
    private boolean closed;
    private boolean examsLoading;
    private boolean executionsLoading;
    private boolean scheduling;
    private boolean submissionsLoading;
    private boolean individualExtensionPending;
    private boolean executionExtensionPending;
    private long examRequestGeneration;
    private long executionRequestGeneration;
    private long scheduleRequestGeneration;
    private long submissionRequestGeneration;

    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private ComboBox<ExamSummaryDTO> approvedExamComboBox;
    @FXML private DatePicker openingDatePicker;
    @FXML private Spinner<Integer> openingHourSpinner;
    @FXML private Spinner<Integer> openingMinuteSpinner;
    @FXML private DatePicker closingDatePicker;
    @FXML private Spinner<Integer> closingHourSpinner;
    @FXML private Spinner<Integer> closingMinuteSpinner;
    @FXML private TextField executionCodeField;
    @FXML private Button scheduleButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Label feedbackLabel;
    @FXML private Label generatedCodeLabel;
    @FXML private Label generatedDetailsLabel;
    @FXML private TableView<ExamExecutionSummaryDTO> executionTable;
    @FXML private TableColumn<ExamExecutionSummaryDTO, Number> executionIdColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> examTitleColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> examCodeColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, Number> versionColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> executionCodeColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> openingColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> closingColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, Number> durationColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> statusColumn;
    @FXML private Label extensionSummaryLabel;
    @FXML private TableView<ExecutionSubmissionSummaryDTO> submissionTable;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, Number> submissionIdColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> studentColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> submissionStatusColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> deadlineColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, Number> individualMinutesColumn;
    @FXML private TextField individualMinutesField;
    @FXML private TextField individualReasonField;
    @FXML private Button extendStudentButton;
    @FXML private TextField executionMinutesField;
    @FXML private TextField executionReasonField;
    @FXML private Button extendExecutionButton;

    @FXML
    private void initialize() {
        configureExamSelector();
        configureTimeControls();
        configureExecutionTable();
        configureSubmissionTable();
        setFeedback("Waiting for authenticated teacher or coordinator.");
        showGeneratedExecution(null);
        updateActionState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable backHandler) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.client = Objects.requireNonNull(client, "client");
        this.loginResult = Objects.requireNonNull(loginResult, "loginResult");
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        requireManager(loginResult);
        this.examClientController = new ExamClientController(client);
        this.executionClientController = new ExamExecutionClientController(client);
        this.closed = false;

        userLabel.setText(loginResult.getFullName());
        roleLabel.setText(loginResult.getRole().name());
        setFeedback("Loading approved exams and scheduled executions...");
        updateActionState();
        loadApprovedExams();
        loadExecutions(null, null);
    }

    private void configureExamSelector() {
        approvedExamComboBox.setItems(approvedExams);
        approvedExamComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ExamSummaryDTO exam) {
                if (exam == null) {
                    return "";
                }
                return safe(exam.getExamCode()) + " — " + safe(exam.getTitle())
                        + " (version " + exam.getVersionNo() + ")";
            }

            @Override
            public ExamSummaryDTO fromString(String value) {
                return null;
            }
        });
        approvedExamComboBox.valueProperty().addListener(
                (observable, oldExam, newExam) -> updateActionState()
        );
    }

    private void configureTimeControls() {
        openingHourSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 9)
        );
        openingMinuteSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0)
        );
        closingHourSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 10)
        );
        closingMinuteSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0)
        );
        LocalDateTime opening = LocalDateTime.now()
                .plusHours(1)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        LocalDateTime closing = opening.plusHours(1);
        openingDatePicker.setValue(opening.toLocalDate());
        openingHourSpinner.getValueFactory().setValue(opening.getHour());
        openingMinuteSpinner.getValueFactory().setValue(opening.getMinute());
        closingDatePicker.setValue(closing.toLocalDate());
        closingHourSpinner.getValueFactory().setValue(closing.getHour());
        closingMinuteSpinner.getValueFactory().setValue(closing.getMinute());
    }

    private void configureExecutionTable() {
        executionTable.setItems(executions);
        executionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        executionIdColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getExecutionId()));
        examTitleColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getExamTitle())));
        examCodeColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getExamCode())));
        versionColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getExamVersionNo()));
        executionCodeColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getExecutionCode())));
        openingColumn.setCellValueFactory(data ->
                new SimpleStringProperty(formatDateTime(data.getValue().getOpeningTime())));
        closingColumn.setCellValueFactory(data ->
                new SimpleStringProperty(formatDateTime(data.getValue().getClosingTime())));
        durationColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getDurationMinutes()));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus() == null
                        ? ""
                        : data.getValue().getStatus().name()
        ));
        executionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> selectExecutionForExtensions(selected)
        );
    }

    private void configureSubmissionTable() {
        submissionTable.setItems(submissions);
        submissionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        submissionIdColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getSubmissionId()));
        studentColumn.setCellValueFactory(data -> new SimpleStringProperty(
                safe(data.getValue().getStudentName()) + " (#"
                        + data.getValue().getStudentUserId() + ")"
        ));
        submissionStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus() == null ? ""
                        : data.getValue().getStatus().name()
        ));
        deadlineColumn.setCellValueFactory(data -> new SimpleStringProperty(
                formatDateTime(data.getValue().getDeadline())
        ));
        individualMinutesColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(
                        data.getValue().getIndividualExtensionMinutes()
                ));
        submissionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    individualMinutesField.clear();
                    individualReasonField.clear();
                    updateActionState();
                }
        );
    }

    private void selectExecutionForExtensions(ExamExecutionSummaryDTO selected) {
        submissionRequestGeneration++;
        submissions.clear();
        submissionTable.getSelectionModel().clearSelection();
        individualMinutesField.clear();
        individualReasonField.clear();
        executionMinutesField.clear();
        executionReasonField.clear();
        if (selected == null || closed) {
            extensionSummaryLabel.setText("Select an execution to manage time extensions.");
            submissionsLoading = false;
            updateActionState();
            return;
        }
        extensionSummaryLabel.setText(
                "Effective duration: " + selected.getDurationMinutes()
                        + " minutes · cumulative execution extension: "
                        + selected.getCumulativeExtensionMinutes() + " minutes"
        );
        if (!isExtensionEligible(selected)) {
            extensionSummaryLabel.setText(
                    "Time extensions are available only while the execution "
                            + "access window is open."
            );
            submissionsLoading = false;
            updateActionState();
            return;
        }
        loadSubmissions(selected.getExecutionId(), null);
    }

    private void loadSubmissions(int executionId, String completionMessage) {
        long generation = ++submissionRequestGeneration;
        submissionsLoading = true;
        updateActionState();
        executionClientController.getExecutionSubmissions(executionId)
                .whenComplete((loaded, error) -> Platform.runLater(() -> {
                    ExamExecutionSummaryDTO selected =
                            executionTable.getSelectionModel().getSelectedItem();
                    if (closed || generation != submissionRequestGeneration
                            || selected == null
                            || selected.getExecutionId() != executionId) return;
                    submissionsLoading = false;
                    if (error != null) {
                        setFeedback(cleanError(error));
                    } else {
                        submissions.setAll(loaded);
                        if (completionMessage != null) setFeedback(completionMessage);
                    }
                    updateActionState();
                }));
    }

    @FXML
    private void handleExtendStudent() {
        ExecutionSubmissionSummaryDTO selected =
                submissionTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getStatus() != SubmissionStatus.IN_PROGRESS
                || individualExtensionPending) return;
        Integer minutes = parsePositiveMinutes(individualMinutesField.getText());
        if (minutes == null) {
            setFeedback("Minutes must be a positive whole number.");
            return;
        }
        String reason = individualReasonField.getText();
        if (reason == null || reason.trim().isEmpty()) {
            setFeedback("Extension reason is required");
            return;
        }
        individualExtensionPending = true;
        updateActionState();
        executionClientController.extendSubmissionTime(
                new ExtendSubmissionTimePayload(
                        selected.getSubmissionId(), minutes, reason.trim()
                )
        ).whenComplete((unused, error) -> Platform.runLater(() -> {
            individualExtensionPending = false;
            ExamExecutionSummaryDTO execution =
                    executionTable.getSelectionModel().getSelectedItem();
            if (closed || execution == null) return;
            if (error != null) {
                setFeedback(cleanError(error));
                updateActionState();
            } else {
                individualMinutesField.clear();
                individualReasonField.clear();
                loadSubmissions(execution.getExecutionId(),
                        "Selected Student received additional time.");
            }
        }));
    }

    @FXML
    private void handleExtendExecution() {
        ExamExecutionSummaryDTO selected =
                executionTable.getSelectionModel().getSelectedItem();
        if (selected == null || executionExtensionPending) return;
        Integer minutes = parsePositiveMinutes(executionMinutesField.getText());
        if (minutes == null) {
            setFeedback("Minutes must be a positive whole number.");
            return;
        }
        String reason = executionReasonField.getText();
        if (reason == null || reason.trim().isEmpty()) {
            setFeedback("Extension reason is required");
            return;
        }
        executionExtensionPending = true;
        updateActionState();
        executionClientController.extendExecutionTime(new ExtendExecutionTimePayload(
                selected.getExecutionId(), minutes, reason.trim()
        )).whenComplete((updated, error) -> Platform.runLater(() -> {
            executionExtensionPending = false;
            if (closed) return;
            if (error != null) {
                setFeedback(cleanError(error));
                updateActionState();
            } else {
                executionMinutesField.clear();
                executionReasonField.clear();
                loadExecutions(updated.getExecutionId(),
                        "Entire execution received additional time.");
            }
        }));
    }

    private void loadApprovedExams() {
        long generation = ++examRequestGeneration;
        examsLoading = true;
        updateActionState();
        examClientController.getMyExams().whenComplete((summaries, error) ->
                Platform.runLater(() -> {
                    if (isStale(closed, generation, examRequestGeneration)) {
                        return;
                    }
                    examsLoading = false;
                    if (error != null) {
                        setFeedback(cleanError(error));
                        updateActionState();
                        return;
                    }

                    ExamSummaryDTO previous = approvedExamComboBox.getValue();
                    approvedExams.setAll(approvedOnly(summaries));
                    selectApprovedExam(previous);
                    if (approvedExams.isEmpty()) {
                        setFeedback("No approved exams are available for scheduling.");
                    } else if (!executionsLoading) {
                        setFeedback("Approved exams and executions loaded.");
                    }
                    updateActionState();
                })
        );
    }

    private void loadExecutions(Integer selectExecutionId, String completionMessage) {
        long generation = ++executionRequestGeneration;
        executionsLoading = true;
        updateActionState();
        executionClientController.getMyExamExecutions().whenComplete((loaded, error) ->
                Platform.runLater(() -> {
                    if (isStale(closed, generation, executionRequestGeneration)) {
                        return;
                    }
                    executionsLoading = false;
                    if (error != null) {
                        setFeedback(cleanError(error));
                        updateActionState();
                        return;
                    }

                    executions.setAll(loaded);
                    selectExecution(selectExecutionId);
                    if (completionMessage != null) {
                        setFeedback(completionMessage);
                    } else if (loaded.isEmpty()) {
                        setFeedback("No exam executions have been scheduled yet.");
                    } else if (!examsLoading) {
                        setFeedback("Approved exams and executions loaded.");
                    }
                    updateActionState();
                })
        );
    }

    @FXML
    private void handleSchedule() {
        ExamSummaryDTO exam = approvedExamComboBox.getValue();
        if (!canStartScheduling(closed, scheduling, exam)) {
            if (!closed && !scheduling && exam == null) {
                setFeedback("Select an approved exam to schedule.");
            }
            return;
        }
        if (exam.getStatus() != ExamStatus.APPROVED) {
            setFeedback("Select an approved exam to schedule.");
            return;
        }

        LocalDate openingDate = openingDatePicker.getValue();
        LocalDate closingDate = closingDatePicker.getValue();
        if (openingDate == null || closingDate == null) {
            setFeedback("Opening and closing dates are required.");
            return;
        }

        LocalDateTime openingTime = dateTime(
                openingDate,
                openingHourSpinner.getValue(),
                openingMinuteSpinner.getValue()
        );
        LocalDateTime closingTime = dateTime(
                closingDate,
                closingHourSpinner.getValue(),
                closingMinuteSpinner.getValue()
        );
        if (!closingTime.isAfter(openingTime)) {
            setFeedback("Execution closing time must be after opening time");
            return;
        }
        if (openingTime.isBefore(LocalDateTime.now())) {
            setFeedback("Execution opening time cannot be in the past");
            return;
        }

        String requestedCode = executionCodeField.getText();
        if (requestedCode != null && !requestedCode.isBlank()
                && !requestedCode.trim().toUpperCase(java.util.Locale.ROOT)
                        .matches("[A-Z0-9]{4}")) {
            setFeedback("Execution code must be exactly 4 letters or digits");
            return;
        }

        ScheduleExamExecutionPayload payload = buildSchedulePayload(
                exam,
                openingTime,
                closingTime,
                requestedCode
        );
        long generation = ++scheduleRequestGeneration;
        scheduling = true;
        setFeedback("Scheduling exam execution...");
        updateActionState();
        executionClientController.scheduleExamExecution(payload).whenComplete(
                (scheduled, error) -> Platform.runLater(() -> {
                    if (isStale(closed, generation, scheduleRequestGeneration)) {
                        return;
                    }
                    scheduling = false;
                    if (error != null) {
                        setFeedback(cleanError(error));
                        updateActionState();
                        return;
                    }

                    showGeneratedExecution(scheduled);
                    loadExecutions(
                            scheduled.getExecutionId(),
                            "Exam execution scheduled successfully."
                    );
                    updateActionState();
                })
        );
    }

    @FXML
    private void handleRefresh() {
        if (closed) {
            return;
        }
        setFeedback("Refreshing approved exams and executions...");
        loadApprovedExams();
        loadExecutions(null, null);
    }

    @FXML
    private void handleBack() {
        if (closed || backHandler == null) {
            return;
        }
        Runnable navigation = backHandler;
        closed = true;
        examRequestGeneration++;
        executionRequestGeneration++;
        scheduleRequestGeneration++;
        submissionRequestGeneration++;
        backHandler = null;
        examClientController = null;
        executionClientController = null;
        client = null;
        loginResult = null;
        stage = null;
        navigation.run();
    }

    private void selectApprovedExam(ExamSummaryDTO previous) {
        ExamSummaryDTO selection = null;
        if (previous != null) {
            for (ExamSummaryDTO exam : approvedExams) {
                if (exam.getExamId() == previous.getExamId()
                        && exam.getVersionNo() == previous.getVersionNo()) {
                    selection = exam;
                    break;
                }
            }
        }
        if (selection == null && !approvedExams.isEmpty()) {
            selection = approvedExams.get(0);
        }
        approvedExamComboBox.setValue(selection);
    }

    private void selectExecution(Integer executionId) {
        if (executionId == null) {
            return;
        }
        for (ExamExecutionSummaryDTO execution : executions) {
            if (execution.getExecutionId() == executionId) {
                executionTable.getSelectionModel().select(execution);
                executionTable.scrollTo(execution);
                return;
            }
        }
    }

    private void showGeneratedExecution(ExamExecutionSummaryDTO execution) {
        if (execution == null) {
            generatedCodeLabel.setText("—");
            generatedDetailsLabel.setText("Schedule an approved exam to generate a code.");
            return;
        }
        generatedCodeLabel.setText(safe(execution.getExecutionCode()));
        generatedDetailsLabel.setText(
                safe(execution.getExamTitle())
                        + " · version " + execution.getExamVersionNo()
                        + " · " + formatDateTime(execution.getOpeningTime())
                        + " to " + formatDateTime(execution.getClosingTime())
                        + " · " + execution.getDurationMinutes() + " minutes"
                        + " · " + (execution.getStatus() == null
                        ? ""
                        : execution.getStatus().name())
        );
    }

    private void updateActionState() {
        boolean configured = !closed
                && client != null
                && examClientController != null
                && executionClientController != null;
        if (scheduleButton != null) {
            scheduleButton.setDisable(
                    !configured || scheduling || approvedExamComboBox.getValue() == null
            );
        }
        if (refreshButton != null) {
            refreshButton.setDisable(!configured || examsLoading || executionsLoading);
        }
        if (busyIndicator != null) {
            boolean busy = examsLoading || executionsLoading || scheduling
                    || submissionsLoading || individualExtensionPending
                    || executionExtensionPending;
            busyIndicator.setVisible(busy);
            busyIndicator.setManaged(busy);
        }
        ExamExecutionSummaryDTO selectedExecution = executionTable == null
                ? null : executionTable.getSelectionModel().getSelectedItem();
        ExecutionSubmissionSummaryDTO selectedSubmission = submissionTable == null
                ? null : submissionTable.getSelectionModel().getSelectedItem();
        if (extendStudentButton != null) {
            extendStudentButton.setDisable(!configured || submissionsLoading
                    || individualExtensionPending || executionExtensionPending
                    || !isExtensionEligible(selectedExecution)
                    || selectedSubmission == null
                    || selectedSubmission.getStatus() != SubmissionStatus.IN_PROGRESS);
        }
        if (extendExecutionButton != null) {
            extendExecutionButton.setDisable(!configured || submissionsLoading
                    || individualExtensionPending || executionExtensionPending
                    || !isExtensionEligible(selectedExecution));
        }
    }

    private void setFeedback(String message) {
        feedbackLabel.setText(message == null || message.isBlank()
                ? "Unable to complete the request"
                : message);
    }

    static String cleanError(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank()
                ? "Unable to complete the request"
                : message;
    }

    static List<ExamSummaryDTO> approvedOnly(List<ExamSummaryDTO> summaries) {
        List<ExamSummaryDTO> result = new ArrayList<>();
        for (ExamSummaryDTO summary : Objects.requireNonNull(summaries, "summaries")) {
            if (summary != null && summary.getStatus() == ExamStatus.APPROVED) {
                result.add(summary);
            }
        }
        return List.copyOf(result);
    }

    static ScheduleExamExecutionPayload buildSchedulePayload(
            ExamSummaryDTO exam,
            LocalDateTime openingTime,
            LocalDateTime closingTime
    ) {
        return buildSchedulePayload(exam, openingTime, closingTime, null);
    }

    /**
     * Builds the scheduling request including the execution code the teacher
     * typed. A blank code is sent as {@code null}, which asks the server to
     * choose one.
     */
    static ScheduleExamExecutionPayload buildSchedulePayload(
            ExamSummaryDTO exam,
            LocalDateTime openingTime,
            LocalDateTime closingTime,
            String executionCode
    ) {
        Objects.requireNonNull(exam, "exam");
        String requestedCode = executionCode == null || executionCode.isBlank()
                ? null
                : executionCode.trim().toUpperCase(java.util.Locale.ROOT);
        return new ScheduleExamExecutionPayload(
                exam.getExamId(),
                exam.getVersionNo(),
                openingTime,
                closingTime,
                requestedCode
        );
    }

    static boolean isStale(boolean closed, long responseGeneration,
                           long currentGeneration) {
        return closed || responseGeneration != currentGeneration;
    }

    static boolean canStartScheduling(boolean closed, boolean scheduling,
                                      ExamSummaryDTO selectedExam) {
        return !closed && !scheduling && selectedExam != null;
    }

    static Integer parsePositiveMinutes(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            int minutes = Integer.parseInt(value.trim());
            return minutes > 0 ? minutes : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static boolean isSchedulingRole(UserRole role) {
        return role == UserRole.TEACHER || role == UserRole.COORDINATOR;
    }

    static boolean isExtensionEligible(ExamExecutionSummaryDTO execution) {
        return execution != null && execution.getStatus() == ExecutionStatus.OPEN;
    }

    private static LocalDateTime dateTime(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute));
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void requireManager(LoginResult loginResult) {
        UserRole role = loginResult.getRole();
        if (!isSchedulingRole(role)) {
            throw new IllegalArgumentException(
                    "Exam scheduling is available only to teachers and coordinators"
            );
        }
    }
}
