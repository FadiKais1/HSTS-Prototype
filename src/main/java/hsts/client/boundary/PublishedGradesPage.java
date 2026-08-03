package hsts.client.boundary;

import hsts.client.control.ExamExecutionClientController;
import hsts.client.net.Client;
import hsts.client.net.ServerEventBus;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import hsts.common.LoginResult;
import hsts.common.PublishedExamQuestionReviewDTO;
import hsts.common.PublishedExamReviewDTO;
import hsts.common.PublishedGradeDTO;
import hsts.common.PublishedGradeSummaryDTO;
import hsts.common.type.UserRole;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;

public class PublishedGradesPage {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String LIST_ERROR = "Unable to load published grades";
    private static final String DETAIL_ERROR = "Unable to load published grade";
    private static final String REVIEW_ERROR = "Unable to load reviewed exam";

    private final ObservableList<PublishedGradeSummaryDTO> grades =
            FXCollections.observableArrayList();

    private Stage stage;
    private Client client;
    private LoginResult loginResult;
    private Runnable backHandler;
    private ExamExecutionClientController executionController;
    private PublishedGradeDTO currentGrade;
    private PublishedExamReviewDTO currentReview;
    private int currentQuestionIndex;
    /** This screen's own push registration; closing it affects no other screen. */
    private ServerEventBus.Subscription eventSubscription;

    private boolean disposed = true;
    private boolean listBusy;
    private boolean detailBusy;
    private boolean reviewBusy;
    private boolean suppressSelection;
    private long listRequestGeneration;
    private long detailRequestGeneration;
    private long reviewRequestGeneration;
    private QuestionIllustrationRenderer illustrationRenderer;

    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private TableView<PublishedGradeSummaryDTO> gradeTable;
    @FXML private TableColumn<PublishedGradeSummaryDTO, String> examTitleColumn;
    @FXML private TableColumn<PublishedGradeSummaryDTO, String> courseColumn;
    @FXML private TableColumn<PublishedGradeSummaryDTO, String> finalScoreColumn;
    @FXML private TableColumn<PublishedGradeSummaryDTO, String> submittedColumn;
    @FXML private TableColumn<PublishedGradeSummaryDTO, String> publishedColumn;
    @FXML private Label detailExamTitleValue;
    @FXML private Label detailCourseValue;
    @FXML private Label detailFinalScoreValue;
    @FXML private Label submittedValue;
    @FXML private Label reviewedValue;
    @FXML private Label publishedValue;
    @FXML private TextArea feedbackArea;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Label reviewStatusLabel;
    @FXML private Label reviewExamValue;
    @FXML private Label reviewCourseValue;
    @FXML private Label reviewExecutionValue;
    @FXML private Label reviewVersionValue;
    @FXML private Label reviewFinalScoreValue;
    @FXML private Label reviewSubmittedValue;
    @FXML private Label reviewReviewedValue;
    @FXML private Label reviewPublishedValue;
    @FXML private TextArea reviewFeedbackArea;
    @FXML private TextArea reviewAdjustmentReasonArea;
    @FXML private Label questionPositionLabel;
    @FXML private Label questionOutcomeLabel;
    @FXML private Label questionMetadataLabel;
    @FXML private Label questionScoreLabel;
    @FXML private TextArea reviewQuestionContentArea;
    @FXML private VBox answerOptionsBox;
    @FXML private Button previousQuestionButton;
    @FXML private Button nextQuestionButton;
    @FXML private VBox questionIllustrationContainer;
    @FXML private ImageView questionIllustrationView;
    @FXML private Label questionIllustrationErrorLabel;

    @FXML
    private void initialize() {
        configureGradeTable();
        illustrationRenderer = new QuestionIllustrationRenderer(
                questionIllustrationView, questionIllustrationErrorLabel,
                questionIllustrationContainer
        );
        clearDetail();
        clearReview();
        setStatus("Published grades will appear here.");
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
        if (loginResult.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException(
                    "Published grades are available only to students"
            );
        }

        this.executionController = new ExamExecutionClientController(client);
        this.disposed = false;
        // A grade published by a teacher reaches this student without a manual
        // refresh. The event carries no grade data; the reload asks the server,
        // which applies the usual authorisation, so only her own results arrive.
        closeEventSubscription();
        this.eventSubscription = client.getServerEventBus().subscribe(
                this::onServerEvent, ServerEventType.GRADES_PUBLISHED
        );
        this.listBusy = false;
        this.detailBusy = false;
        this.reviewBusy = false;
        this.currentGrade = null;
        this.currentReview = null;
        this.listRequestGeneration++;
        this.detailRequestGeneration++;
        this.reviewRequestGeneration++;

        userLabel.setText(safe(loginResult.getFullName()));
        roleLabel.setText(loginResult.getRole().name());
        grades.clear();
        clearDetail();
        clearReview();
        loadPublishedGrades(null);
    }

    private void configureGradeTable() {
        gradeTable.setItems(grades);
        examTitleColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(safe(cell.getValue().getExamTitle())));
        courseColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(safe(cell.getValue().getCourseName())));
        finalScoreColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDecimal(cell.getValue().getFinalScore())));
        submittedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDateTime(cell.getValue().getSubmittedAt())));
        publishedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDateTime(cell.getValue().getPublishedAt())));
        gradeTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    if (!suppressSelection && selected != null) {
                        loadPublishedResult(selected.getSubmissionId());
                    }
                }
        );
    }

    private void loadPublishedGrades(Integer preferredSubmissionId) {
        if (!isConfigured()) {
            return;
        }

        long generation = ++listRequestGeneration;
        detailRequestGeneration++;
        reviewRequestGeneration++;
        listBusy = true;
        detailBusy = false;
        reviewBusy = false;
        clearDetail();
        clearReview();
        setStatus("Loading published grades...");
        updateControlState();

        executionController.getMyPublishedGrades().whenComplete((loaded, error) ->
                Platform.runLater(() -> {
                    if (isStale(disposed, generation, listRequestGeneration)) {
                        return;
                    }
                    listBusy = false;
                    if (error != null) {
                        grades.clear();
                        clearSelection();
                        clearDetail();
                        clearReview();
                        setStatus(cleanError(error, LIST_ERROR));
                        updateControlState();
                        return;
                    }

                    grades.setAll(preserveServerOrder(loaded));
                    PublishedGradeSummaryDTO selected =
                            selectGrade(preferredSubmissionId);
                    if (grades.isEmpty()) {
                        clearDetail();
                        clearReview();
                        setStatus("No published grades are available.");
                    } else if (selected == null) {
                        clearDetail();
                        clearReview();
                        setStatus("Select a published grade to view details.");
                    }
                    updateControlState();
                })
        );
    }

    private void loadPublishedGrade(int submissionId) {
        if (!isConfigured()) {
            return;
        }

        long generation = ++detailRequestGeneration;
        detailBusy = true;
        currentGrade = null;
        clearDetailValues();
        setStatus("Loading published grade...");
        updateControlState();

        executionController.getMyPublishedGrade(submissionId)
                .whenComplete((loaded, error) -> Platform.runLater(() -> {
                    if (isStale(disposed, generation, detailRequestGeneration)
                            || selectedSubmissionId() != submissionId) {
                        return;
                    }
                    detailBusy = false;
                    if (error != null || loaded == null) {
                        clearDetailValues();
                        setStatus(error == null
                                ? DETAIL_ERROR
                                : cleanError(error, DETAIL_ERROR));
                    } else {
                        showGrade(loaded);
                        setStatus("Published grade loaded.");
                    }
                    updateControlState();
                }));
    }

    private void loadPublishedResult(int submissionId) {
        reviewRequestGeneration++;
        clearReview();
        loadPublishedGrade(submissionId);
        loadPublishedExamReview(submissionId);
    }

    private void loadPublishedExamReview(int submissionId) {
        if (!isConfigured()) return;
        long generation = ++reviewRequestGeneration;
        reviewBusy = true;
        reviewStatusLabel.setText("Loading reviewed exam...");
        updateControlState();
        executionController.getMyPublishedExamReview(submissionId)
                .whenComplete((loaded, error) -> Platform.runLater(() -> {
                    if (isStale(disposed, generation, reviewRequestGeneration)
                            || selectedSubmissionId() != submissionId) {
                        return;
                    }
                    reviewBusy = false;
                    if (error != null || loaded == null) {
                        clearReviewValues();
                        reviewStatusLabel.setText(error == null
                                ? REVIEW_ERROR : cleanError(error, REVIEW_ERROR));
                    } else {
                        showReview(loaded);
                        reviewStatusLabel.setText(
                                "Published reviewed exam loaded."
                        );
                    }
                    updateControlState();
                }));
    }

    /**
     * Reacts to a teacher publishing a grade. Runs on the JavaFX thread.
     *
     * <p>The reload keeps the currently selected result, so a student reading
     * one paper is not thrown back to the top of the list when another grade
     * arrives.</p>
     */
    private void onServerEvent(ServerEvent event) {
        if (disposed || event == null
                || event.getType() != ServerEventType.GRADES_PUBLISHED) {
            return;
        }
        if (!isConfigured() || listBusy) {
            return;
        }
        refreshPreservingSelection("A new grade was published.");
    }

    private void refreshPreservingSelection(String message) {
        PublishedGradeSummaryDTO selected =
                gradeTable.getSelectionModel().getSelectedItem();
        Integer preferredSubmissionId = selected == null
                ? null : selected.getSubmissionId();
        listRequestGeneration++;
        detailRequestGeneration++;
        reviewRequestGeneration++;
        if (message != null) {
            setStatus(message);
        }
        loadPublishedGrades(preferredSubmissionId);
    }

    private void closeEventSubscription() {
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
        }
    }

    @FXML
    private void handleRefresh() {
        if (!isConfigured() || listBusy) {
            return;
        }
        refreshPreservingSelection(null);
    }

    @FXML
    private void handleBack() {
        if (disposed || backHandler == null) {
            setStatus("Back navigation is unavailable.");
            return;
        }

        Runnable navigation = backHandler;
        disposed = true;
        closeEventSubscription();
        listRequestGeneration++;
        detailRequestGeneration++;
        reviewRequestGeneration++;
        listBusy = false;
        detailBusy = false;
        reviewBusy = false;
        try {
            navigation.run();
            illustrationRenderer.dispose();
        } catch (RuntimeException exception) {
            disposed = false;
            setStatus("Unable to return to dashboard.");
            updateControlState();
        }
    }

    private void showGrade(PublishedGradeDTO grade) {
        currentGrade = Objects.requireNonNull(grade, "grade");
        detailExamTitleValue.setText(safe(grade.getExamTitle()));
        detailCourseValue.setText(safe(grade.getCourseName()));
        detailFinalScoreValue.setText(formatDecimal(grade.getFinalScore()));
        submittedValue.setText(formatDateTime(grade.getSubmittedAt()));
        reviewedValue.setText(formatDateTime(grade.getReviewedAt()));
        publishedValue.setText(formatDateTime(grade.getPublishedAt()));
        feedbackArea.setText(friendlyFeedback(grade.getTeacherFeedback()));
    }

    private void showReview(PublishedExamReviewDTO review) {
        currentReview = Objects.requireNonNull(review, "review");
        currentQuestionIndex = 0;
        reviewExamValue.setText(safe(review.getExamTitle()));
        reviewCourseValue.setText(safe(review.getCourseName()));
        reviewExecutionValue.setText(safe(review.getExecutionCode()));
        reviewVersionValue.setText(Integer.toString(review.getExamVersionNo()));
        reviewFinalScoreValue.setText(formatDecimal(review.getFinalScore()));
        reviewSubmittedValue.setText(formatDateTime(review.getSubmittedAt()));
        reviewReviewedValue.setText(formatDateTime(review.getReviewedAt()));
        reviewPublishedValue.setText(formatDateTime(review.getPublishedAt()));
        reviewFeedbackArea.setText(friendlyFeedback(review.getTeacherFeedback()));
        if (reviewAdjustmentReasonArea != null) {
            reviewAdjustmentReasonArea.setText(
                    friendlyAdjustmentReason(review.getManualChangeReason())
            );
        }
        showCurrentReviewQuestion();
    }

    @FXML
    private void handlePreviousQuestion() {
        if (currentReview == null || currentQuestionIndex <= 0) return;
        currentQuestionIndex--;
        showCurrentReviewQuestion();
    }

    @FXML
    private void handleNextQuestion() {
        if (currentReview == null
                || currentQuestionIndex >= currentReview.getQuestions().size() - 1) {
            return;
        }
        currentQuestionIndex++;
        showCurrentReviewQuestion();
    }

    private void showCurrentReviewQuestion() {
        if (currentReview == null || currentReview.getQuestions().isEmpty()) {
            clearQuestionReview();
            return;
        }
        PublishedExamQuestionReviewDTO question =
                currentReview.getQuestions().get(currentQuestionIndex);
        questionPositionLabel.setText("Question " + (currentQuestionIndex + 1)
                + " of " + currentReview.getQuestions().size());
        questionMetadataLabel.setText(
                "Topic: " + displayMetadata(question.getTopic())
                        + "  |  Type: " + displayMetadata(question.getType())
                        + "  |  Difficulty: "
                        + displayMetadata(question.getDifficulty())
        );
        questionScoreLabel.setText("Awarded "
                + formatDecimal(question.getAwardedScore()) + " / "
                + formatDecimal(question.getMaximumScore()));
        reviewQuestionContentArea.setText(question.getContent());
        illustrationRenderer.render(question.getIllustration());
        showOutcome(question);
        answerOptionsBox.getChildren().clear();
        for (int index = 0; index < question.getAnswerOptions().size(); index++) {
            answerOptionsBox.getChildren().add(optionLabel(question, index + 1));
        }
        previousQuestionButton.setDisable(currentQuestionIndex == 0);
        nextQuestionButton.setDisable(
                currentQuestionIndex == currentReview.getQuestions().size() - 1
        );
    }

    private void showOutcome(PublishedExamQuestionReviewDTO question) {
        switch (question.getOutcome()) {
            case CORRECT -> {
                questionOutcomeLabel.setText("Correct");
                questionOutcomeLabel.setStyle(outcomeStyle("#166534", "#dcfce7"));
            }
            case INCORRECT -> {
                questionOutcomeLabel.setText("Incorrect");
                questionOutcomeLabel.setStyle(outcomeStyle("#991b1b", "#fee2e2"));
            }
            case UNANSWERED -> {
                questionOutcomeLabel.setText("Unanswered");
                questionOutcomeLabel.setStyle(outcomeStyle("#92400e", "#fef3c7"));
            }
        }
    }

    private Label optionLabel(PublishedExamQuestionReviewDTO question,
                              int optionNumber) {
        boolean selected = Objects.equals(
                question.getSelectedOptionNumber(), optionNumber
        );
        boolean correct = question.getCorrectOptionNumber() == optionNumber;
        StringBuilder text = new StringBuilder("Option ")
                .append(optionNumber).append(": ")
                .append(question.getAnswerOptions().get(optionNumber - 1));
        if (selected) text.append("  · Your answer");
        if (correct) text.append("  · Correct answer");
        if (selected && !correct) text.append("  · Incorrect");
        Label label = new Label(text.toString());
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        String border = correct ? "#16a34a" : selected ? "#dc2626" : "#cbd5e1";
        String background = correct ? "#f0fdf4" : selected ? "#fef2f2" : "#f8fafc";
        label.setStyle("-fx-text-fill: #111827; -fx-opacity: 1; "
                + "-fx-background-color: " + background + "; "
                + "-fx-border-color: " + border + "; -fx-border-radius: 7; "
                + "-fx-background-radius: 7; -fx-padding: 10;");
        return label;
    }

    private PublishedGradeSummaryDTO selectGrade(Integer submissionId) {
        clearSelection();
        if (submissionId == null) {
            return null;
        }
        for (int index = 0; index < grades.size(); index++) {
            PublishedGradeSummaryDTO grade = grades.get(index);
            if (grade.getSubmissionId() == submissionId) {
                gradeTable.getSelectionModel().select(index);
                gradeTable.scrollTo(index);
                loadPublishedResult(submissionId);
                return grade;
            }
        }
        return null;
    }

    private void clearSelection() {
        suppressSelection = true;
        try {
            gradeTable.getSelectionModel().clearSelection();
        } finally {
            suppressSelection = false;
        }
    }

    private int selectedSubmissionId() {
        PublishedGradeSummaryDTO selected =
                gradeTable.getSelectionModel().getSelectedItem();
        return selected == null ? 0 : selected.getSubmissionId();
    }

    private void clearDetail() {
        currentGrade = null;
        clearDetailValues();
    }

    private void clearDetailValues() {
        if (detailExamTitleValue != null) detailExamTitleValue.setText("-");
        if (detailCourseValue != null) detailCourseValue.setText("-");
        if (detailFinalScoreValue != null) detailFinalScoreValue.setText("-");
        if (submittedValue != null) submittedValue.setText("");
        if (reviewedValue != null) reviewedValue.setText("");
        if (publishedValue != null) publishedValue.setText("");
        if (feedbackArea != null) feedbackArea.setText("");
    }

    private void clearReview() {
        currentReview = null;
        currentQuestionIndex = 0;
        clearReviewValues();
        if (reviewStatusLabel != null) {
            reviewStatusLabel.setText(
                    "Select a published grade to review the exam."
            );
        }
    }

    private void clearReviewValues() {
        if (reviewExamValue != null) reviewExamValue.setText("-");
        if (reviewCourseValue != null) reviewCourseValue.setText("-");
        if (reviewExecutionValue != null) reviewExecutionValue.setText("-");
        if (reviewVersionValue != null) reviewVersionValue.setText("-");
        if (reviewFinalScoreValue != null) reviewFinalScoreValue.setText("-");
        if (reviewSubmittedValue != null) reviewSubmittedValue.setText("");
        if (reviewReviewedValue != null) reviewReviewedValue.setText("");
        if (reviewPublishedValue != null) reviewPublishedValue.setText("");
        if (reviewFeedbackArea != null) reviewFeedbackArea.setText("");
        clearQuestionReview();
    }

    private void clearQuestionReview() {
        if (questionPositionLabel != null) {
            questionPositionLabel.setText("No question selected");
        }
        if (questionOutcomeLabel != null) questionOutcomeLabel.setText("");
        if (questionMetadataLabel != null) questionMetadataLabel.setText("");
        if (questionScoreLabel != null) questionScoreLabel.setText("");
        if (reviewQuestionContentArea != null) {
            reviewQuestionContentArea.setText("");
        }
        if (illustrationRenderer != null) illustrationRenderer.render(null);
        if (answerOptionsBox != null) answerOptionsBox.getChildren().clear();
        if (previousQuestionButton != null) previousQuestionButton.setDisable(true);
        if (nextQuestionButton != null) nextQuestionButton.setDisable(true);
    }

    private void updateControlState() {
        boolean configured = isConfigured();
        boolean busy = listBusy || detailBusy || reviewBusy;
        if (refreshButton != null) {
            refreshButton.setDisable(!configured || listBusy);
        }
        if (backButton != null) {
            backButton.setDisable(!configured);
        }
        if (gradeTable != null) {
            gradeTable.setDisable(!configured || listBusy);
        }
        if (busyIndicator != null) {
            busyIndicator.setManaged(busy);
            busyIndicator.setVisible(busy);
        }
    }

    private boolean isConfigured() {
        return !disposed && stage != null && client != null
                && loginResult != null && executionController != null;
    }

    static List<PublishedGradeSummaryDTO> preserveServerOrder(
            List<PublishedGradeSummaryDTO> loaded
    ) {
        return List.copyOf(Objects.requireNonNull(loaded, "loaded"));
    }

    static boolean isStale(boolean disposed, long responseGeneration,
                           long currentGeneration) {
        return disposed || responseGeneration != currentGeneration;
    }

    /**
     * The teacher's justification for changing a grade by hand (requirement 39).
     * Students see the same text the teacher was required to supply.
     */
    static String friendlyAdjustmentReason(String reason) {
        return reason == null || reason.isBlank()
                ? "Your grade was not adjusted manually." : reason;
    }

    static String friendlyFeedback(String feedback) {
        return feedback == null || feedback.isBlank()
                ? "No feedback provided" : feedback;
    }

    static String cleanError(Throwable error, String fallback) {
        Throwable current = error;
        while ((current instanceof CompletionException || current.getCause() != null)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value);
    }

    static String formatDecimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String displayMetadata(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private static String outcomeStyle(String textColor, String background) {
        return "-fx-font-weight: bold; -fx-text-fill: " + textColor
                + "; -fx-background-color: " + background
                + "; -fx-background-radius: 7; -fx-padding: 6 10; "
                + "-fx-opacity: 1;";
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
