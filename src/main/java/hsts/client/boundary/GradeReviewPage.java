package hsts.client.boundary;

import hsts.client.control.ExamExecutionClientController;
import hsts.client.net.Client;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.LoginResult;
import hsts.common.PublishSubmissionPayload;
import hsts.common.ReviewSubmissionPayload;
import hsts.common.SubmissionAnswerReviewDTO;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.SubmissionStatus;
import hsts.common.type.UserRole;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GradeReviewPage {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SAFE_ERROR = "Unable to complete the request.";

    private final ObservableList<ExamExecutionSummaryDTO> executions =
            FXCollections.observableArrayList();
    private final ObservableList<ExecutionSubmissionSummaryDTO> submissions =
            FXCollections.observableArrayList();
    private final ObservableList<SubmissionAnswerReviewDTO> answers =
            FXCollections.observableArrayList();

    private Stage stage;
    private Client client;
    private LoginResult loginResult;
    private Runnable backHandler;
    private ExamExecutionClientController executionClientController;
    private SubmissionReviewDTO currentReview;
    private boolean disposed;
    private boolean mutationInProgress;
    private boolean executionsLoading;
    private boolean submissionsLoading;
    private boolean detailLoading;
    private boolean suppressSubmissionSelection;
    private long executionRequestGeneration;
    private long submissionRequestGeneration;
    private long detailRequestGeneration;
    private long mutationRequestGeneration;
    private QuestionIllustrationRenderer illustrationRenderer;

    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private ComboBox<ExamExecutionSummaryDTO> executionComboBox;
    @FXML private TableView<ExecutionSubmissionSummaryDTO> submissionTable;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> studentColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, Number> studentIdColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, Number> submissionIdColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> submissionStatusColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> submittedColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> automaticScoreColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> finalScoreColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> reviewedColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> publishedColumn;
    @FXML private Label examTitleValue;
    @FXML private Label studentValue;
    @FXML private Label detailStatusValue;
    @FXML private Label automaticScoreValue;
    @FXML private Label timestampsValue;
    @FXML private TableView<SubmissionAnswerReviewDTO> answerTable;
    @FXML private TableColumn<SubmissionAnswerReviewDTO, Number> answerOrderColumn;
    @FXML private TableColumn<SubmissionAnswerReviewDTO, String> questionColumn;
    @FXML private TableColumn<SubmissionAnswerReviewDTO, String> selectedAnswerColumn;
    @FXML private TableColumn<SubmissionAnswerReviewDTO, String> correctnessColumn;
    @FXML private TableColumn<SubmissionAnswerReviewDTO, String> awardedColumn;
    @FXML private TableColumn<SubmissionAnswerReviewDTO, String> maximumColumn;
    @FXML private TextField finalScoreField;
    @FXML private TextArea feedbackArea;
    @FXML private TextArea adjustmentReasonArea;
    @FXML private Button saveReviewButton;
    @FXML private Button publishGradeButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator busyIndicator;
    @FXML private VBox questionIllustrationContainer;
    @FXML private ImageView questionIllustrationView;
    @FXML private Label questionIllustrationErrorLabel;

    @FXML
    private void initialize() {
        configureExecutionSelector();
        configureSubmissionTable();
        configureAnswerTable();
        illustrationRenderer = new QuestionIllustrationRenderer(
                questionIllustrationView, questionIllustrationErrorLabel,
                questionIllustrationContainer
        );
        clearSubmissionState();
        updateControlState();
    }

    public void configure(
            Stage stage,
            Client client,
            LoginResult loginResult,
            Runnable backHandler
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.client = Objects.requireNonNull(client, "client");
        this.loginResult = Objects.requireNonNull(loginResult, "loginResult");
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        requireManagerRole(loginResult);
        this.executionClientController = new ExamExecutionClientController(client);
        // Server-pushed changes keep this screen current without a manual refresh.
        client.setServerEventListener(this::onServerEvent);
        disposed = false;
        mutationInProgress = false;
        userLabel.setText(loginResult.getFullName());
        roleLabel.setText(loginResult.getRole().name());
        loadExecutions(null);
    }

    private void configureExecutionSelector() {
        executionComboBox.setItems(executions);
        executionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ExamExecutionSummaryDTO execution) {
                if (execution == null) {
                    return "";
                }
                return execution.getExamTitle() + " (" + execution.getExecutionCode()
                        + ", ID " + execution.getExecutionId() + ")";
            }

            @Override
            public ExamExecutionSummaryDTO fromString(String value) {
                return null;
            }
        });
        executionComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> handleExecutionSelection(selected)
        );
    }

    private void configureSubmissionTable() {
        submissionTable.setItems(submissions);
        studentColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(safe(cell.getValue().getStudentName())));
        studentIdColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getStudentUserId()));
        submissionIdColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getSubmissionId()));
        submissionStatusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(statusText(cell.getValue().getStatus())));
        submittedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDateTime(cell.getValue().getSubmittedAt())));
        automaticScoreColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDecimal(cell.getValue().getAutomaticScore())));
        finalScoreColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDecimal(cell.getValue().getFinalScore())));
        reviewedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDateTime(cell.getValue().getReviewedAt())));
        publishedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDateTime(cell.getValue().getPublishedAt())));
        submissionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    if (!suppressSubmissionSelection && selected != null) {
                        loadSubmissionDetail(selected.getSubmissionId(), null);
                    }
                }
        );
    }

    private void configureAnswerTable() {
        answerTable.setItems(answers);
        answerOrderColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getOrderNumber()));
        questionColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(safe(cell.getValue().getQuestionContent())));
        selectedAnswerColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getSelectedOptionText() == null
                        ? "Unanswered" : cell.getValue().getSelectedOptionText()
        ));
        correctnessColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(correctnessText(cell.getValue().getCorrect())));
        awardedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDecimal(cell.getValue().getAwardedScore())));
        maximumColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDecimal(cell.getValue().getMaximumScore())));
        answerTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldAnswer, answer) -> illustrationRenderer.render(
                        answer == null ? null : answer.getIllustration()
                )
        );
    }

    private void loadExecutions(Integer preferredExecutionId) {
        if (!isConfigured()) {
            return;
        }
        long generation = ++executionRequestGeneration;
        executionsLoading = true;
        setStatus("Loading managed exam executions...");
        updateControlState();
        executionClientController.getMyExamExecutions().whenComplete((loaded, error) ->
                Platform.runLater(() -> {
                    if (isStale(disposed, generation, executionRequestGeneration)) {
                        return;
                    }
                    executionsLoading = false;
                    if (error != null) {
                        executions.clear();
                        setStatus(cleanError(error));
                        updateControlState();
                        return;
                    }
                    executions.setAll(loaded);
                    ExamExecutionSummaryDTO selected = selectExecution(preferredExecutionId);
                    if (selected == null && !executions.isEmpty()) {
                        executionComboBox.getSelectionModel().selectFirst();
                    } else if (executions.isEmpty()) {
                        clearSubmissionState();
                        setStatus("No managed exam executions are available.");
                    }
                    updateControlState();
                })
        );
    }

    private void handleExecutionSelection(ExamExecutionSummaryDTO selected) {
        submissionRequestGeneration++;
        detailRequestGeneration++;
        submissionsLoading = false;
        detailLoading = false;
        submissions.clear();
        submissionTable.getSelectionModel().clearSelection();
        clearSubmissionState();
        if (selected != null && isConfigured()) {
            loadSubmissions(selected.getExecutionId(), null, null);
        }
    }

    private void loadSubmissions(
            int executionId,
            Integer preferredSubmissionId,
            String completionMessage
    ) {
        long generation = ++submissionRequestGeneration;
        detailRequestGeneration++;
        detailLoading = false;
        submissionsLoading = true;
        setStatus("Loading submissions...");
        updateControlState();
        executionClientController.getExecutionSubmissions(executionId)
                .whenComplete((loaded, error) -> Platform.runLater(() -> {
                    if (isStale(disposed, generation, submissionRequestGeneration)
                            || selectedExecutionId() != executionId) {
                        return;
                    }
                    submissionsLoading = false;
                    if (error != null) {
                        submissions.clear();
                        clearSubmissionState();
                        setStatus(cleanError(error));
                        updateControlState();
                        return;
                    }
                    submissions.setAll(loaded);
                    ExecutionSubmissionSummaryDTO selected =
                            selectSubmission(preferredSubmissionId);
                    if (submissions.isEmpty()) {
                        clearSubmissionState();
                        setStatus(completionMessage == null
                                ? "No submissions are available for this execution."
                                : completionMessage);
                    } else if (selected == null && completionMessage == null) {
                        setStatus("Select a submission to review.");
                    } else if (selected != null) {
                        loadSubmissionDetail(
                                selected.getSubmissionId(), completionMessage
                        );
                    }
                    updateControlState();
                }));
    }

    private void loadSubmissionDetail(int submissionId, String completionMessage) {
        long generation = ++detailRequestGeneration;
        detailLoading = true;
        currentReview = null;
        answers.clear();
        illustrationRenderer.render(null);
        setStatus("Loading submission details...");
        updateControlState();
        executionClientController.getSubmissionForReview(submissionId)
                .whenComplete((review, error) -> Platform.runLater(() -> {
                    if (isStale(disposed, generation, detailRequestGeneration)
                            || selectedSubmissionId() != submissionId) {
                        return;
                    }
                    detailLoading = false;
                    if (error != null) {
                        clearSubmissionState();
                        setStatus(cleanError(error));
                    } else {
                        showReview(review);
                        setStatus(completionMessage == null
                                ? "Submission details loaded."
                                : completionMessage);
                    }
                    updateControlState();
                }));
    }

    @FXML
    private void handleSaveReview() {
        SubmissionReviewDTO review = currentReview;
        if (review == null || mutationInProgress || !canReview(review)) {
            if (review == null) {
                setStatus("Select and load a reviewable submission first.");
            }
            return;
        }

        final BigDecimal finalScore;
        try {
            finalScore = parseFinalScore(finalScoreField.getText());
            requireAdjustmentReason(
                    review.getAutomaticScore(),
                    finalScore,
                    adjustmentReasonArea.getText()
            );
        } catch (IllegalArgumentException exception) {
            setStatus(exception.getMessage());
            return;
        }

        ReviewSubmissionPayload payload = new ReviewSubmissionPayload(
                review.getSubmissionId(),
                finalScore,
                feedbackArea.getText(),
                adjustmentReasonArea.getText(),
                review.getUpdatedAt()
        );
        beginMutation("Saving grade review...");
        long generation = ++mutationRequestGeneration;
        executionClientController.reviewSubmissionGrade(payload)
                .whenComplete((updated, error) -> Platform.runLater(() ->
                        finishMutation(
                                generation,
                                review.getSubmissionId(),
                                updated,
                                error,
                                "Grade review saved successfully."
                        )
                ));
    }

    @FXML
    private void handlePublishGrade() {
        SubmissionReviewDTO review = currentReview;
        if (review == null || mutationInProgress || !canPublish(review)) {
            if (review == null) {
                setStatus("Select and load a reviewed submission first.");
            }
            return;
        }

        PublishSubmissionPayload payload = new PublishSubmissionPayload(
                review.getSubmissionId(),
                review.getUpdatedAt()
        );
        beginMutation("Publishing grade...");
        long generation = ++mutationRequestGeneration;
        executionClientController.publishSubmissionGrade(payload)
                .whenComplete((updated, error) -> Platform.runLater(() ->
                        finishMutation(
                                generation,
                                review.getSubmissionId(),
                                updated,
                                error,
                                "Grade published successfully."
                        )
                ));
    }

    private void beginMutation(String message) {
        mutationInProgress = true;
        setStatus(message);
        updateControlState();
    }

    private void finishMutation(
            long generation,
            int submissionId,
            SubmissionReviewDTO updated,
            Throwable error,
            String successMessage
    ) {
        if (isStale(disposed, generation, mutationRequestGeneration)) {
            return;
        }
        mutationInProgress = false;
        if (error != null) {
            String message = cleanError(error);
            setStatus(message);
            updateControlState();
            if (isConcurrencyConflict(message)) {
                loadSubmissionDetail(submissionId, message);
            }
            return;
        }

        showReview(updated);
        int executionId = selectedExecutionId();
        if (executionId > 0) {
            loadSubmissions(executionId, submissionId, successMessage);
        } else {
            setStatus(successMessage);
            updateControlState();
        }
    }

    /**
     * Reacts to server-pushed changes.
     *
     * <p>Deliberately conservative: while a submission detail is open the
     * reviewer may be part-way through typing a score or feedback, so only the
     * list levels are reloaded. Nothing the reviewer has typed is discarded.</p>
     */
    private void onServerEvent(ServerEvent event) {
        if (event == null) {
            return;
        }
        ServerEventType type = event.getType();
        if (type != ServerEventType.SUBMISSION_RECEIVED
                && type != ServerEventType.GRADES_PUBLISHED
                && type != ServerEventType.EXAM_TIME_EXTENDED
                && type != ServerEventType.NOTIFICATION_CREATED) {
            return;
        }

        Platform.runLater(() -> {
            if (disposed || !isConfigured() || mutationInProgress) {
                return;
            }
            if (selectedSubmissionId() > 0) {
                // A review is open; leave it untouched.
                return;
            }
            int executionId = selectedExecutionId();
            if (executionId > 0) {
                loadSubmissions(executionId, null, null);
            } else {
                loadExecutions(null);
            }
        });
    }

    @FXML
    private void handleRefresh() {
        if (!isConfigured() || mutationInProgress) {
            return;
        }
        int executionId = selectedExecutionId();
        int submissionId = selectedSubmissionId();
        if (submissionId > 0) {
            loadSubmissionDetail(submissionId, "Submission refreshed.");
        } else if (executionId > 0) {
            loadSubmissions(executionId, null, null);
        } else {
            loadExecutions(null);
        }
    }

    @FXML
    private void handleBack() {
        if (backHandler == null || disposed) {
            setStatus("Back navigation is unavailable.");
            return;
        }
        disposed = true;
        if (client != null) {
            client.setServerEventListener(null);
        }
        executionRequestGeneration++;
        submissionRequestGeneration++;
        detailRequestGeneration++;
        mutationRequestGeneration++;
        try {
            backHandler.run();
            illustrationRenderer.dispose();
        } catch (RuntimeException exception) {
            disposed = false;
            setStatus("Unable to return to dashboard.");
            updateControlState();
        }
    }

    private void showReview(SubmissionReviewDTO review) {
        currentReview = Objects.requireNonNull(review, "review");
        examTitleValue.setText(safe(review.getExamTitle()));
        studentValue.setText(
                safe(review.getStudentName()) + " (user "
                        + review.getStudentUserId() + ", submission "
                        + review.getSubmissionId() + ")"
        );
        detailStatusValue.setText(statusText(review.getStatus()));
        automaticScoreValue.setText(formatDecimal(review.getAutomaticScore()));
        timestampsValue.setText(
                "Started: " + formatDateTime(review.getStartedAt())
                        + "   Submitted: " + formatDateTime(review.getSubmittedAt())
                        + "   Reviewed: " + formatDateTime(review.getReviewedAt())
                        + "   Published: " + formatDateTime(review.getPublishedAt())
        );
        finalScoreField.setText(formatDecimal(review.getFinalScore()));
        feedbackArea.setText(safe(review.getTeacherFeedback()));
        adjustmentReasonArea.setText(safe(review.getAdjustmentReason()));
        answers.setAll(review.getAnswers());
        updateControlState();
    }

    private void clearSubmissionState() {
        currentReview = null;
        answers.clear();
        if (examTitleValue != null) examTitleValue.setText("-");
        if (studentValue != null) studentValue.setText("-");
        if (detailStatusValue != null) detailStatusValue.setText("-");
        if (automaticScoreValue != null) automaticScoreValue.setText("");
        if (timestampsValue != null) timestampsValue.setText("");
        if (finalScoreField != null) finalScoreField.clear();
        if (feedbackArea != null) feedbackArea.clear();
        if (adjustmentReasonArea != null) adjustmentReasonArea.clear();
        updateControlState();
    }

    private ExamExecutionSummaryDTO selectExecution(Integer executionId) {
        executionComboBox.getSelectionModel().clearSelection();
        if (executionId == null) {
            return null;
        }
        for (ExamExecutionSummaryDTO execution : executions) {
            if (execution.getExecutionId() == executionId) {
                executionComboBox.getSelectionModel().select(execution);
                return execution;
            }
        }
        return null;
    }

    private ExecutionSubmissionSummaryDTO selectSubmission(Integer submissionId) {
        suppressSubmissionSelection = true;
        try {
            submissionTable.getSelectionModel().clearSelection();
            if (submissionId == null) {
                return null;
            }
            for (int index = 0; index < submissions.size(); index++) {
                ExecutionSubmissionSummaryDTO submission = submissions.get(index);
                if (submission.getSubmissionId() == submissionId) {
                    submissionTable.getSelectionModel().select(index);
                    submissionTable.scrollTo(index);
                    return submission;
                }
            }
            return null;
        } finally {
            suppressSubmissionSelection = false;
        }
    }

    private int selectedExecutionId() {
        ExamExecutionSummaryDTO selected =
                executionComboBox.getSelectionModel().getSelectedItem();
        return selected == null ? 0 : selected.getExecutionId();
    }

    private int selectedSubmissionId() {
        ExecutionSubmissionSummaryDTO selected =
                submissionTable.getSelectionModel().getSelectedItem();
        return selected == null ? 0 : selected.getSubmissionId();
    }

    private void updateControlState() {
        boolean configured = isConfigured();
        boolean reviewable = configured && canReview(currentReview);
        boolean publishable = configured && canPublish(currentReview);
        boolean loading = executionsLoading || submissionsLoading || detailLoading;
        boolean busy = mutationInProgress;
        if (saveReviewButton != null) {
            saveReviewButton.setDisable(!reviewable || busy || loading);
        }
        if (publishGradeButton != null) {
            publishGradeButton.setDisable(!publishable || busy || loading);
        }
        if (refreshButton != null) refreshButton.setDisable(!configured || busy || loading);
        if (executionComboBox != null) {
            executionComboBox.setDisable(!configured || busy || executionsLoading);
        }
        if (submissionTable != null) {
            submissionTable.setDisable(!configured || busy || submissionsLoading);
        }
        if (finalScoreField != null) finalScoreField.setEditable(reviewable && !busy);
        if (feedbackArea != null) feedbackArea.setEditable(reviewable && !busy);
        if (adjustmentReasonArea != null) {
            adjustmentReasonArea.setEditable(reviewable && !busy);
        }
        if (busyIndicator != null) {
            busyIndicator.setManaged(busy || loading);
            busyIndicator.setVisible(busy || loading);
        }
    }

    private boolean isConfigured() {
        return !disposed && stage != null && client != null
                && executionClientController != null;
    }

    static BigDecimal parseFinalScore(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Final score is required");
        }
        final BigDecimal score;
        try {
            score = new BigDecimal(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Final score must be a valid decimal");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Final score must be between 0.00 and 100.00");
        }
        if (score.scale() > 2) {
            throw new IllegalArgumentException(
                    "Final score must have no more than two decimal places"
            );
        }
        return score;
    }

    static void requireAdjustmentReason(
            BigDecimal automaticScore,
            BigDecimal finalScore,
            String adjustmentReason
    ) {
        if (automaticScore != null
                && automaticScore.compareTo(finalScore) != 0
                && (adjustmentReason == null || adjustmentReason.isBlank())) {
            throw new IllegalArgumentException(
                    "Adjustment reason is required when changing the automatic score"
            );
        }
    }

    static boolean canReview(SubmissionReviewDTO review) {
        return review != null
                && isFinalizedUnpublished(review.getStatus())
                && review.getAutomaticScore() != null
                && review.getReviewedAt() == null
                && review.getPublishedAt() == null;
    }

    static boolean canPublish(SubmissionReviewDTO review) {
        return review != null
                && isFinalizedUnpublished(review.getStatus())
                && review.getAutomaticScore() != null
                && review.getFinalScore() != null
                && review.getReviewedAt() != null
                && review.getPublisherUserId() == 0
                && review.getPublishedAt() == null;
    }

    private static boolean isFinalizedUnpublished(SubmissionStatus status) {
        return status == SubmissionStatus.SUBMITTED
                || status == SubmissionStatus.AUTO_SUBMITTED;
    }

    static boolean isStale(boolean disposed, long responseGeneration,
                           long currentGeneration) {
        return disposed || responseGeneration != currentGeneration;
    }

    static boolean isConcurrencyConflict(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("conflict")
                || normalized.contains("state changed");
    }

    private void requireManagerRole(LoginResult login) {
        UserRole role = login.getRole();
        if (role != UserRole.TEACHER && role != UserRole.COORDINATOR) {
            throw new IllegalArgumentException(
                    "Grade review is available only to teachers and coordinators"
            );
        }
    }

    private String cleanError(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? SAFE_ERROR : current.getMessage();
    }

    private static String correctnessText(Boolean correct) {
        if (correct == null) return "Not graded";
        return correct ? "Correct" : "Incorrect";
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value);
    }

    private static String formatDecimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String statusText(SubmissionStatus status) {
        return status == null ? "" : status.name();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }
}
