package hsts.client.boundary;

import hsts.client.control.ExamClientController;
import hsts.client.control.ExamExecutionClientController;
import hsts.client.net.Client;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.LoginResult;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.type.ExamStatus;
import hsts.common.type.UserRole;
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
    private long examRequestGeneration;
    private long executionRequestGeneration;
    private long scheduleRequestGeneration;

    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private ComboBox<ExamSummaryDTO> approvedExamComboBox;
    @FXML private DatePicker openingDatePicker;
    @FXML private Spinner<Integer> openingHourSpinner;
    @FXML private Spinner<Integer> openingMinuteSpinner;
    @FXML private DatePicker closingDatePicker;
    @FXML private Spinner<Integer> closingHourSpinner;
    @FXML private Spinner<Integer> closingMinuteSpinner;
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

    @FXML
    private void initialize() {
        configureExamSelector();
        configureTimeControls();
        configureExecutionTable();
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

        ScheduleExamExecutionPayload payload = buildSchedulePayload(
                exam,
                openingTime,
                closingTime
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
            boolean busy = examsLoading || executionsLoading || scheduling;
            busyIndicator.setVisible(busy);
            busyIndicator.setManaged(busy);
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
        Objects.requireNonNull(exam, "exam");
        return new ScheduleExamExecutionPayload(
                exam.getExamId(),
                exam.getVersionNo(),
                openingTime,
                closingTime
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

    static boolean isSchedulingRole(UserRole role) {
        return role == UserRole.TEACHER || role == UserRole.COORDINATOR;
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
