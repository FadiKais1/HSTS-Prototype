package hsts.client.boundary;

import hsts.client.net.ServerEventBus;
import hsts.client.control.ExamClientController;
import hsts.client.net.Client;
import hsts.common.ExamDTO;
import hsts.common.ExamQuestionDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.ExamVersionPayload;
import hsts.common.RejectExamPayload;
import hsts.common.type.ExamStatus;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ApprovalRequestsPage {
    /** This screen's own push registration; closing it affects no other screen. */
    private ServerEventBus.Subscription eventSubscription;

    private void closeEventSubscription() {
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
        }
    }

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObservableList<ExamSummaryDTO> pendingExams =
            FXCollections.observableArrayList();
    private final ObservableList<ExamQuestionDTO> examQuestions =
            FXCollections.observableArrayList();

    private Stage stage;
    private Client client;
    private Runnable backHandler;
    private ExamClientController examClientController;
    private ExamDTO loadedExam;
    private boolean closed;
    private boolean listLoading;
    private boolean detailLoading;
    private boolean decisionPending;
    private long listRequestGeneration;
    private long detailRequestGeneration;
    private QuestionIllustrationRenderer illustrationRenderer;

    @FXML private TableView<ExamSummaryDTO> pendingExamTable;
    @FXML private TableColumn<ExamSummaryDTO, String> examCodeColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> titleColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> courseColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> subjectColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> creatorColumn;
    @FXML private TableColumn<ExamSummaryDTO, Number> versionColumn;
    @FXML private TableColumn<ExamSummaryDTO, Number> totalColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> submittedColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> statusColumn;

    @FXML private Label examIdValue;
    @FXML private Label examCodeValue;
    @FXML private Label titleValue;
    @FXML private Label courseSubjectValue;
    @FXML private Label creatorValue;
    @FXML private Label versionValue;
    @FXML private Label durationValue;
    @FXML private Label totalValue;
    @FXML private Label statusValue;
    @FXML private Label submittedValue;
    @FXML private Label rejectionReasonValue;
    @FXML private TextArea teacherNotesArea;
    @FXML private TextArea studentInstructionsArea;

    @FXML private TableView<ExamQuestionDTO> questionTable;
    @FXML private TableColumn<ExamQuestionDTO, Number> questionOrderColumn;
    @FXML private TableColumn<ExamQuestionDTO, Number> questionIdColumn;
    @FXML private TableColumn<ExamQuestionDTO, Number> questionVersionColumn;
    @FXML private TableColumn<ExamQuestionDTO, String> questionContentColumn;
    @FXML private TableColumn<ExamQuestionDTO, String> questionTopicColumn;
    @FXML private TableColumn<ExamQuestionDTO, String> questionDifficultyColumn;
    @FXML private TableColumn<ExamQuestionDTO, Number> questionScoreColumn;
    @FXML private TableColumn<ExamQuestionDTO, String> option1Column;
    @FXML private TableColumn<ExamQuestionDTO, String> option2Column;
    @FXML private TableColumn<ExamQuestionDTO, String> option3Column;
    @FXML private TableColumn<ExamQuestionDTO, String> option4Column;
    @FXML private TableColumn<ExamQuestionDTO, Number> correctOptionColumn;

    @FXML private Label feedbackLabel;
    @FXML private Button refreshButton;
    @FXML private Button openButton;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;
    @FXML private Button backButton;
    @FXML private VBox questionIllustrationContainer;
    @FXML private ImageView questionIllustrationView;
    @FXML private Label questionIllustrationErrorLabel;

    @FXML
    private void initialize() {
        configurePendingTable();
        configureQuestionTable();
        illustrationRenderer = new QuestionIllustrationRenderer(
                questionIllustrationView, questionIllustrationErrorLabel,
                questionIllustrationContainer
        );
        questionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldQuestion, question) -> illustrationRenderer.render(
                        question == null ? null : question.getIllustration()
                )
        );
        pendingExamTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldExam, newExam) -> updateActionState()
        );
        clearDetails();
        setFeedback("Waiting for authenticated coordinator.");
        updateActionState();
    }

    public void configure(Stage stage, Client client, Runnable backHandler) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.client = Objects.requireNonNull(client, "client");
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        this.examClientController = new ExamClientController(client);
        // Newly submitted exams appear without the coordinator refreshing.
        this.eventSubscription =
                client.getServerEventBus().subscribe(this::onServerEvent);
        this.closed = false;
        setFeedback("Loading pending exams...");
        updateActionState();
        loadPendingExams(null, null, false, false);
    }

    private void configurePendingTable() {
        pendingExamTable.setItems(pendingExams);
        pendingExamTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        examCodeColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getExamCode())));
        titleColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getTitle())));
        courseColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getCourseName())));
        subjectColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getSubjectName())));
        creatorColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getCreatorName())));
        versionColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getVersionNo()));
        totalColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getTotalScore()));
        submittedColumn.setCellValueFactory(data ->
                new SimpleStringProperty(formatDateTime(data.getValue().getSubmittedAt())));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus() == null ? "" : data.getValue().getStatus().name()
        ));
    }

    private void configureQuestionTable() {
        questionTable.setItems(examQuestions);
        questionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        questionOrderColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getOrderNumber()));
        questionIdColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuestionId()));
        questionVersionColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuestionVersionNo()));
        questionContentColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getContent())));
        questionTopicColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getTopic())));
        questionDifficultyColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getDifficulty())));
        questionScoreColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getScore()));
        option1Column.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getAnswerOption1())));
        option2Column.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getAnswerOption2())));
        option3Column.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getAnswerOption3())));
        option4Column.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getAnswerOption4())));
        correctOptionColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getCorrectOptionNumber()));
    }

    private void loadPendingExams(Integer selectExamId, String completionMessage,
                                  boolean clearWhenMissing, boolean reopenWhenPresent) {
        long generation = ++listRequestGeneration;
        listLoading = true;
        updateActionState();
        examClientController.getPendingExams().whenComplete((summaries, error) ->
                Platform.runLater(() -> {
                    if (closed || generation != listRequestGeneration) {
                        return;
                    }
                    listLoading = false;
                    if (error != null) {
                        setFeedback(cleanError(error));
                        updateActionState();
                        return;
                    }

                    pendingExams.setAll(summaries);
                    ExamSummaryDTO selected = selectSummary(selectExamId);
                    if (selectExamId != null && selected == null && clearWhenMissing) {
                        clearDetails();
                    }
                    if (selected != null && reopenWhenPresent) {
                        openExam(selected.getExamId(), completionMessage);
                    } else if (completionMessage != null) {
                        setFeedback(completionMessage);
                    } else if (summaries.isEmpty()) {
                        setFeedback("No pending exams are awaiting review.");
                    } else {
                        setFeedback("Pending exams loaded.");
                    }
                    updateActionState();
                })
        );
    }

    /**
     * Reloads the pending list when an exam is submitted, approved or rejected,
     * so the approval queue stays current without a manual refresh.
     */
    private void onServerEvent(ServerEvent event) {
        if (event == null
                || event.getType() != ServerEventType.EXAM_APPROVAL_CHANGED) {
            return;
        }
        Platform.runLater(() -> {
            if (closed) {
                return;
            }
            Integer examId = loadedExam == null ? null : loadedExam.getExamId();
            loadPendingExams(examId, null, true, false);
        });
    }

    @FXML
    private void handleRefresh() {
        Integer examId = loadedExam == null ? null : loadedExam.getExamId();
        loadPendingExams(examId, null, true, false);
    }

    @FXML
    private void handleOpen() {
        ExamSummaryDTO selected = pendingExamTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setFeedback("Select a pending exam to open.");
            return;
        }
        openExam(selected.getExamId(), null);
    }

    private void openExam(int examId, String completionMessage) {
        long generation = ++detailRequestGeneration;
        detailLoading = true;
        updateActionState();
        examClientController.getPendingExam(examId).whenComplete((exam, error) ->
                Platform.runLater(() -> {
                    if (closed || generation != detailRequestGeneration) {
                        return;
                    }
                    detailLoading = false;
                    if (error != null) {
                        setFeedback(cleanError(error));
                    } else {
                        showExam(exam);
                        setFeedback(completionMessage == null
                                ? "Pending exam loaded."
                                : completionMessage);
                    }
                    updateActionState();
                })
        );
    }

    private void showExam(ExamDTO exam) {
        loadedExam = Objects.requireNonNull(exam);
        examIdValue.setText(String.valueOf(exam.getExamId()));
        examCodeValue.setText(safe(exam.getExamCode()));
        titleValue.setText(safe(exam.getTitle()));
        courseSubjectValue.setText(
                safe(exam.getCourseName()) + " — " + safe(exam.getSubjectName())
        );
        creatorValue.setText(safe(exam.getCreatorName()));
        versionValue.setText(String.valueOf(exam.getVersionNo()));
        durationValue.setText(exam.getDurationMinutes() + " minutes");
        totalValue.setText(String.valueOf(exam.getTotalScore()));
        statusValue.setText(exam.getStatus() == null ? "" : exam.getStatus().name());
        submittedValue.setText(formatDateTime(exam.getSubmittedAt()));
        teacherNotesArea.setText(safe(exam.getTeacherNotes()));
        studentInstructionsArea.setText(safe(exam.getStudentInstructions()));
        rejectionReasonValue.setText(safe(exam.getRejectionReason()));
        examQuestions.setAll(orderedSnapshots(exam.getQuestions()));
        updateActionState();
    }

    @FXML
    private void handleApprove() {
        if (!canReviewLoadedExam() || decisionPending) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Approve exam");
        confirmation.setHeaderText("Approve " + safe(loadedExam.getExamCode()) + "?");
        confirmation.setContentText("This explicitly approves the loaded exam version.");
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }

        ExamVersionPayload payload = new ExamVersionPayload(
                loadedExam.getExamId(),
                loadedExam.getVersionNo()
        );
        decisionPending = true;
        updateActionState();
        setFeedback("Approving exam...");
        examClientController.approveExam(payload).whenComplete((approved, error) ->
                finishDecision(approved, error, "Exam approved successfully."));
    }

    @FXML
    private void handleReject() {
        if (!canReviewLoadedExam() || decisionPending) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reject exam");
        dialog.setHeaderText("Reject " + safe(loadedExam.getExamCode()));
        dialog.setContentText("Required reason:");
        String reason = dialog.showAndWait().orElse(null);
        if (reason == null) {
            return;
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.isEmpty()) {
            setFeedback("Rejection reason is required");
            return;
        }

        RejectExamPayload payload = new RejectExamPayload(
                loadedExam.getExamId(),
                loadedExam.getVersionNo(),
                trimmedReason
        );
        decisionPending = true;
        updateActionState();
        setFeedback("Rejecting exam...");
        examClientController.rejectExam(payload).whenComplete((rejected, error) ->
                finishDecision(rejected, error, "Exam rejected successfully."));
    }

    private void finishDecision(ExamDTO result, Throwable error, String successMessage) {
        Platform.runLater(() -> {
            if (closed) {
                return;
            }
            decisionPending = false;
            if (error != null) {
                String message = cleanError(error);
                setFeedback(message);
                if ("Exam version conflict".equals(message) && loadedExam != null) {
                    loadPendingExams(
                            loadedExam.getExamId(),
                            message,
                            true,
                            true
                    );
                } else {
                    updateActionState();
                }
                return;
            }

            showExam(result);
            loadPendingExams(result.getExamId(), successMessage, true, false);
            updateActionState();
        });
    }

    @FXML
    private void handleBack() {
        if (backHandler == null) {
            setFeedback("Back navigation is unavailable.");
            return;
        }
        closed = true;
        if (client != null) {
            closeEventSubscription();
        }
        listRequestGeneration++;
        detailRequestGeneration++;
        try {
            backHandler.run();
            illustrationRenderer.dispose();
        } catch (RuntimeException exception) {
            closed = false;
            setFeedback("Unable to return to dashboard.");
        }
    }

    private ExamSummaryDTO selectSummary(Integer examId) {
        pendingExamTable.getSelectionModel().clearSelection();
        if (examId == null) {
            return null;
        }
        for (int index = 0; index < pendingExams.size(); index++) {
            ExamSummaryDTO summary = pendingExams.get(index);
            if (summary.getExamId() == examId) {
                pendingExamTable.getSelectionModel().select(index);
                pendingExamTable.scrollTo(index);
                return summary;
            }
        }
        return null;
    }

    private void clearDetails() {
        loadedExam = null;
        examIdValue.setText("-");
        examCodeValue.setText("-");
        titleValue.setText("-");
        courseSubjectValue.setText("-");
        creatorValue.setText("-");
        versionValue.setText("-");
        durationValue.setText("-");
        totalValue.setText("-");
        statusValue.setText("-");
        submittedValue.setText("-");
        rejectionReasonValue.setText("");
        teacherNotesArea.clear();
        studentInstructionsArea.clear();
        examQuestions.clear();
        illustrationRenderer.render(null);
        updateActionState();
    }

    static List<ExamQuestionDTO> orderedSnapshots(List<ExamQuestionDTO> snapshots) {
        List<ExamQuestionDTO> ordered = new ArrayList<>(snapshots);
        ordered.sort(Comparator.comparingInt(ExamQuestionDTO::getOrderNumber));
        return List.copyOf(ordered);
    }

    static boolean canReview(ExamStatus status) {
        return status == ExamStatus.PENDING_APPROVAL;
    }

    private boolean canReviewLoadedExam() {
        return loadedExam != null && canReview(loadedExam.getStatus());
    }

    private void updateActionState() {
        boolean configured = isConfigured();
        boolean selected = pendingExamTable != null
                && pendingExamTable.getSelectionModel().getSelectedItem() != null;
        if (refreshButton != null) refreshButton.setDisable(
                !configured || listLoading || decisionPending
        );
        if (openButton != null) openButton.setDisable(
                !configured || detailLoading || decisionPending || !selected
        );
        if (approveButton != null) approveButton.setDisable(
                !configured || listLoading || detailLoading
                        || decisionPending || !canReviewLoadedExam()
        );
        if (rejectButton != null) rejectButton.setDisable(
                !configured || listLoading || detailLoading
                        || decisionPending || !canReviewLoadedExam()
        );
    }

    private boolean isConfigured() {
        return !closed && client != null && examClientController != null;
    }

    private String cleanError(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? "Unable to complete the request."
                : current.getMessage();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void setFeedback(String message) {
        if (feedbackLabel != null) {
            feedbackLabel.setText(message == null ? "" : message);
        }
    }
}
