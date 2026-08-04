package hsts.client.boundary;

import hsts.client.control.PrincipalOversightClientController;
import hsts.client.net.Client;
import hsts.client.net.ServerEventBus;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import hsts.common.ExamDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExamQuestionDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.LoginResult;
import hsts.common.PrincipalQuestionDTO;
import hsts.common.SubmissionAnswerReviewDTO;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.UserRole;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class PrincipalOversightPage {
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CONFIGURATION_ERROR =
            "Principal oversight page is not configured";

    private final ObservableList<PrincipalQuestionDTO> questions =
            FXCollections.observableArrayList();
    private final ObservableList<ExamSummaryDTO> exams =
            FXCollections.observableArrayList();
    private final ObservableList<ExamExecutionSummaryDTO> executions =
            FXCollections.observableArrayList();
    private final ObservableList<ExecutionSubmissionSummaryDTO> submissions =
            FXCollections.observableArrayList();

    private Stage stage;
    private Client client;
    private LoginResult loginResult;
    private Runnable backHandler;
    private PrincipalOversightClientController controller;
    /** This screen's own push registration; closing it affects no other screen. */
    private ServerEventBus.Subscription eventSubscription;

    private boolean disposed;
    private boolean refreshing;
    private long questionGeneration;
    private long examGeneration;
    private long executionGeneration;
    private long submissionGeneration;
    private List<ExamQuestionDTO> currentExamQuestions = List.of();
    private List<SubmissionAnswerReviewDTO> currentSubmissionAnswers = List.of();
    private QuestionIllustrationRenderer questionIllustrationRenderer;
    private QuestionIllustrationRenderer examIllustrationRenderer;
    private QuestionIllustrationRenderer submissionIllustrationRenderer;

    @FXML private Label identityLabel;
    @FXML private Label errorLabel;
    @FXML private Button backButton;
    @FXML private Button refreshButton;
    @FXML private TabPane oversightTabs;

    @FXML private TableView<PrincipalQuestionDTO> questionTable;
    @FXML private TableColumn<PrincipalQuestionDTO, String> questionIdColumn;
    @FXML private TableColumn<PrincipalQuestionDTO, String> questionCourseColumn;
    @FXML private TableColumn<PrincipalQuestionDTO, String> questionCreatorColumn;
    @FXML private TableColumn<PrincipalQuestionDTO, String> questionTopicColumn;
    @FXML private TableColumn<PrincipalQuestionDTO, String> questionStatusColumn;
    @FXML private ComboBox<Integer> questionVersionCombo;
    @FXML private TextArea questionDetailArea;
    @FXML private ListView<String> questionOptionsList;

    @FXML private TableView<ExamSummaryDTO> examTable;
    @FXML private TableColumn<ExamSummaryDTO, String> examCodeColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> examTitleColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> examCourseColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> examCreatorColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> examStatusColumn;
    @FXML private ComboBox<Integer> examVersionCombo;
    @FXML private TextArea examDetailArea;
    @FXML private ListView<String> examQuestionsList;

    @FXML private TableView<ExamExecutionSummaryDTO> executionTable;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> executionCodeColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> executionExamColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> executionCourseColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> executionStatusColumn;
    @FXML private TableColumn<ExamExecutionSummaryDTO, String> executionWindowColumn;
    @FXML private TableView<ExecutionSubmissionSummaryDTO> submissionTable;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> studentColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> submissionStatusColumn;
    @FXML private TableColumn<ExecutionSubmissionSummaryDTO, String> finalScoreColumn;
    @FXML private TextArea submissionDetailArea;
    @FXML private ListView<String> submissionAnswersList;
    @FXML private VBox questionIllustrationContainer;
    @FXML private ImageView questionIllustrationView;
    @FXML private Label questionIllustrationErrorLabel;
    @FXML private VBox examIllustrationContainer;
    @FXML private ImageView examIllustrationView;
    @FXML private Label examIllustrationErrorLabel;
    @FXML private VBox submissionIllustrationContainer;
    @FXML private ImageView submissionIllustrationView;
    @FXML private Label submissionIllustrationErrorLabel;

    @FXML
    private void initialize() {
        configureColumns();
        questionTable.setItems(questions);
        examTable.setItems(exams);
        executionTable.setItems(executions);
        submissionTable.setItems(submissions);
        questionIllustrationRenderer = new QuestionIllustrationRenderer(
                questionIllustrationView, questionIllustrationErrorLabel,
                questionIllustrationContainer
        );
        examIllustrationRenderer = new QuestionIllustrationRenderer(
                examIllustrationView, examIllustrationErrorLabel,
                examIllustrationContainer
        );
        submissionIllustrationRenderer = new QuestionIllustrationRenderer(
                submissionIllustrationView, submissionIllustrationErrorLabel,
                submissionIllustrationContainer
        );
        questionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> selectQuestion(selected));
        examTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> selectExam(selected));
        executionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> selectExecution(selected));
        submissionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> selectSubmission(selected));
        questionVersionCombo.valueProperty().addListener(
                (observable, oldValue, version) -> loadQuestionVersion(version));
        examVersionCombo.valueProperty().addListener(
                (observable, oldValue, version) -> loadExamVersion(version));
        examQuestionsList.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldValue, index) -> examIllustrationRenderer.render(
                        index.intValue() < 0 || index.intValue() >= currentExamQuestions.size()
                                ? null : currentExamQuestions.get(index.intValue()).getIllustration()
                )
        );
        submissionAnswersList.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldValue, index) -> submissionIllustrationRenderer.render(
                        index.intValue() < 0 || index.intValue() >= currentSubmissionAnswers.size()
                                ? null : currentSubmissionAnswers.get(index.intValue()).getIllustration()
                )
        );
        clearQuestionDetail();
        clearExamDetail();
        clearSubmissionDetail();
        showError("");
    }

    public void configure(
            Stage stage, Client client, LoginResult loginResult, Runnable backHandler
    ) {
        this.stage = Objects.requireNonNull(stage, CONFIGURATION_ERROR);
        this.client = Objects.requireNonNull(client, CONFIGURATION_ERROR);
        this.loginResult = Objects.requireNonNull(loginResult, CONFIGURATION_ERROR);
        this.backHandler = Objects.requireNonNull(backHandler, CONFIGURATION_ERROR);
        if (loginResult.getRole() != UserRole.PRINCIPAL) {
            throw new IllegalArgumentException(CONFIGURATION_ERROR);
        }
        this.controller = new PrincipalOversightClientController(client);
        this.disposed = false;
        this.refreshing = false;
        // Oversight is read only, but what it oversees changes constantly, so
        // the Principal should not have to press Refresh to see it (NFR 17).
        closeEventSubscription();
        this.eventSubscription = client.getServerEventBus().subscribe(
                this::onServerEvent,
                ServerEventType.QUESTION_CHANGED,
                ServerEventType.EXAM_CHANGED,
                ServerEventType.EXAM_APPROVAL_CHANGED,
                ServerEventType.EXAM_SCHEDULE_CHANGED,
                ServerEventType.ATTEMPT_STARTED,
                ServerEventType.SUBMISSION_RECEIVED,
                ServerEventType.GRADES_PUBLISHED
        );
        identityLabel.setText(loginResult.getFullName() + " · PRINCIPAL");
        refreshAll();
    }

    /**
     * Reacts to anything the Principal oversees changing.
     *
     * <p>A reload that arrives while a row is selected would move the detail
     * pane out from under whoever is reading it, so the refresh is held back
     * until nothing is selected. The Refresh button remains for a Principal who
     * wants the current picture immediately.</p>
     */
    private void onServerEvent(ServerEvent event) {
        if (disposed || event == null || controller == null || refreshing) {
            return;
        }
        if (questionTable.getSelectionModel().getSelectedItem() != null
                || examTable.getSelectionModel().getSelectedItem() != null
                || executionTable.getSelectionModel().getSelectedItem() != null) {
            return;
        }
        refreshAll();
    }

    private void closeEventSubscription() {
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
        }
    }

    @FXML
    private void handleRefresh() {
        if (controller == null) {
            showError(CONFIGURATION_ERROR);
            return;
        }
        if (refreshing) return;
        refreshAll();
    }

    @FXML
    private void handleBack() {
        if (backHandler == null) {
            showError(CONFIGURATION_ERROR);
            return;
        }
        disposed = true;
        closeEventSubscription();
        backButton.setDisable(true);
        questionGeneration++;
        examGeneration++;
        executionGeneration++;
        submissionGeneration++;
        backHandler.run();
        questionIllustrationRenderer.dispose();
        examIllustrationRenderer.dispose();
        submissionIllustrationRenderer.dispose();
    }

    private void refreshAll() {
        if (refreshing) return;
        refreshing = true;
        refreshButton.setDisable(true);
        showError("");
        CompletableFuture.allOf(loadQuestions(), loadExams(), loadExecutions())
                .whenComplete((ignored, failure) -> Platform.runLater(() -> {
                    if (disposed) return;
                    refreshing = false;
                    refreshButton.setDisable(false);
                }));
    }

    private CompletableFuture<List<PrincipalQuestionDTO>> loadQuestions() {
        long generation = ++questionGeneration;
        CompletableFuture<List<PrincipalQuestionDTO>> request =
                controller.getAllQuestions();
        complete(request, generation,
                value -> {
                    questions.setAll(value);
                    clearQuestionDetail();
                }, "Unable to load questions", () -> questionGeneration);
        return request;
    }

    private CompletableFuture<List<ExamSummaryDTO>> loadExams() {
        long generation = ++examGeneration;
        CompletableFuture<List<ExamSummaryDTO>> request = controller.getAllExams();
        complete(request, generation,
                value -> {
                    exams.setAll(value);
                    clearExamDetail();
                }, "Unable to load exams", () -> examGeneration);
        return request;
    }

    private CompletableFuture<List<ExamExecutionSummaryDTO>> loadExecutions() {
        long generation = ++executionGeneration;
        CompletableFuture<List<ExamExecutionSummaryDTO>> request =
                controller.getAllExecutions();
        complete(request, generation,
                value -> {
                    executions.setAll(value);
                    submissions.clear();
                    clearSubmissionDetail();
                }, "Unable to load executions", () -> executionGeneration);
        return request;
    }

    private void selectQuestion(PrincipalQuestionDTO selected) {
        questionGeneration++;
        questionVersionCombo.getItems().clear();
        clearQuestionDetail();
        if (selected == null || controller == null) return;
        long generation = questionGeneration;
        complete(controller.getQuestionVersions(selected.getQuestionId()), generation,
                versions -> {
                    questionVersionCombo.setItems(FXCollections.observableArrayList(
                            versions.stream().map(PrincipalQuestionDTO::getVersionNo).toList()
                    ));
                    if (!versions.isEmpty()) {
                        questionVersionCombo.getSelectionModel().selectFirst();
                    }
                }, "Unable to load question history", () -> questionGeneration);
    }

    private void loadQuestionVersion(Integer versionNo) {
        PrincipalQuestionDTO selected = questionTable.getSelectionModel().getSelectedItem();
        if (selected == null || versionNo == null || controller == null) return;
        long generation = ++questionGeneration;
        complete(controller.getQuestionVersion(selected.getQuestionId(), versionNo),
                generation, this::showQuestion,
                "Unable to load question detail", () -> questionGeneration);
    }

    private void showQuestion(PrincipalQuestionDTO question) {
        questionDetailArea.setText(
                "Question " + question.getQuestionId() + " · Version "
                        + question.getVersionNo() + "\nCourse: "
                        + question.getCourseName() + "\nCreator: "
                        + question.getCreatorName() + "\nTopic: " + question.getTopic()
                        + "\nType: " + question.getType() + "\nDifficulty: "
                        + question.getDifficulty() + "\nStatus: " + question.getStatus()
                        + "\nCreated: " + time(question.getVersionCreatedAt())
                        + "\nUpdated: " + time(question.getQuestionUpdatedAt())
                        + "\n\n" + question.getContent()
        );
        questionIllustrationRenderer.render(question.getIllustration());
        questionOptionsList.setItems(FXCollections.observableArrayList(
                java.util.stream.IntStream.range(0, question.getAnswerOptions().size())
                        .mapToObj(index -> (index + 1) + ". "
                                + question.getAnswerOptions().get(index)
                                + (index + 1 == question.getCorrectOptionNumber()
                                ? "  ✓ Correct" : ""))
                        .toList()
        ));
    }

    private void selectExam(ExamSummaryDTO selected) {
        examGeneration++;
        examVersionCombo.getItems().clear();
        clearExamDetail();
        if (selected == null || controller == null) return;
        long generation = examGeneration;
        complete(controller.getExamVersions(selected.getExamId()), generation,
                versions -> {
                    examVersionCombo.setItems(FXCollections.observableArrayList(
                            versions.stream().map(ExamSummaryDTO::getVersionNo).toList()
                    ));
                    if (!versions.isEmpty()) examVersionCombo.getSelectionModel().selectFirst();
                }, "Unable to load exam history", () -> examGeneration);
    }

    private void loadExamVersion(Integer versionNo) {
        ExamSummaryDTO selected = examTable.getSelectionModel().getSelectedItem();
        if (selected == null || versionNo == null || controller == null) return;
        long generation = ++examGeneration;
        complete(controller.getExamVersion(selected.getExamId(), versionNo), generation,
                this::showExam, "Unable to load exam detail", () -> examGeneration);
    }

    private void showExam(ExamDTO exam) {
        examDetailArea.setText(
                exam.getExamCode() + " · Version " + exam.getVersionNo()
                        + "\n" + exam.getTitle() + "\nCourse: " + exam.getCourseName()
                        + "\nCreator: " + exam.getCreatorName() + "\nStatus: "
                        + exam.getStatus() + "\nDuration: " + exam.getDurationMinutes()
                        + " minutes\nTotal score: " + exam.getTotalScore()
                        + "\nCreated: " + time(exam.getCreatedAt())
                        + "\nSubmitted: " + time(exam.getSubmittedAt())
                        + "\nReviewed: " + time(exam.getReviewedAt())
                        + "\nReviewer: " + text(exam.getReviewerName())
                        + "\nRejection: " + text(exam.getRejectionReason())
                        + "\n\nTeacher notes: " + text(exam.getTeacherNotes())
                        + "\nStudent instructions: " + text(exam.getStudentInstructions())
        );
        examQuestionsList.setItems(FXCollections.observableArrayList(
                exam.getQuestions().stream().map(this::examQuestionText).toList()
        ));
        currentExamQuestions = List.copyOf(exam.getQuestions());
        if (!currentExamQuestions.isEmpty()) {
            examQuestionsList.getSelectionModel().selectFirst();
        }
    }

    private String examQuestionText(ExamQuestionDTO question) {
        return question.getOrderNumber() + ". Q" + question.getQuestionId()
                + " v" + question.getQuestionVersionNo() + " — "
                + question.getContent() + " (" + question.getScore() + ")";
    }

    private void selectExecution(ExamExecutionSummaryDTO selected) {
        submissionGeneration++;
        submissions.clear();
        clearSubmissionDetail();
        if (selected == null || controller == null) return;
        long generation = submissionGeneration;
        complete(controller.getExecutionResults(selected.getExecutionId()), generation,
                submissions::setAll, "Unable to load execution results",
                () -> submissionGeneration);
    }

    private void selectSubmission(ExecutionSubmissionSummaryDTO selected) {
        clearSubmissionDetail();
        if (selected == null || controller == null) return;
        long generation = ++submissionGeneration;
        complete(controller.getSubmissionResult(selected.getSubmissionId()), generation,
                this::showSubmission, "Unable to load submission result",
                () -> submissionGeneration);
    }

    private void showSubmission(SubmissionReviewDTO submission) {
        submissionDetailArea.setText(
                "Submission " + submission.getSubmissionId() + " · Execution "
                        + submission.getExecutionId() + "\nStudent: "
                        + submission.getStudentName() + " (#"
                        + submission.getStudentUserId() + ")\nExam: "
                        + submission.getExamTitle() + " v" + submission.getExamVersionNo()
                        + "\nStatus: " + submission.getStatus()
                        + "\nAutomatic score: " + decimal(submission.getAutomaticScore())
                        + "\nFinal score: " + decimal(submission.getFinalScore())
                        + "\nStarted: " + time(submission.getStartedAt())
                        + "\nSubmitted: " + time(submission.getSubmittedAt())
                        + "\nReviewed: " + time(submission.getReviewedAt())
                        + "\nPublished: " + time(submission.getPublishedAt())
                        + "\nFeedback: " + text(submission.getTeacherFeedback())
                        + "\nAdjustment: " + text(submission.getAdjustmentReason())
        );
        submissionAnswersList.setItems(FXCollections.observableArrayList(
                submission.getAnswers().stream().map(this::answerText).toList()
        ));
        currentSubmissionAnswers = List.copyOf(submission.getAnswers());
        if (!currentSubmissionAnswers.isEmpty()) {
            submissionAnswersList.getSelectionModel().selectFirst();
        }
    }

    private String answerText(SubmissionAnswerReviewDTO answer) {
        return answer.getOrderNumber() + ". " + answer.getQuestionContent()
                + " — " + text(answer.getSelectedOptionText()) + " — "
                + decimal(answer.getAwardedScore()) + "/"
                + decimal(answer.getMaximumScore());
    }

    private <T> void complete(
            CompletableFuture<T> future, long generation, Consumer<T> success,
            String safeError, java.util.function.LongSupplier currentGeneration
    ) {
        future.whenComplete((value, failure) -> Platform.runLater(() -> {
            if (disposed || generation != currentGeneration.getAsLong()) return;
            if (failure != null) {
                showError(safeError);
                return;
            }
            showError("");
            success.accept(value);
        }));
    }

    private void configureColumns() {
        questionIdColumn.setCellValueFactory(v -> string(v.getValue().getQuestionId()));
        questionCourseColumn.setCellValueFactory(v -> string(v.getValue().getCourseName()));
        questionCreatorColumn.setCellValueFactory(v -> string(v.getValue().getCreatorName()));
        questionTopicColumn.setCellValueFactory(v -> string(v.getValue().getTopic()));
        questionStatusColumn.setCellValueFactory(v -> string(v.getValue().getStatus()));
        examCodeColumn.setCellValueFactory(v -> string(v.getValue().getExamCode()));
        examTitleColumn.setCellValueFactory(v -> string(v.getValue().getTitle()));
        examCourseColumn.setCellValueFactory(v -> string(v.getValue().getCourseName()));
        examCreatorColumn.setCellValueFactory(v -> string(v.getValue().getCreatorName()));
        examStatusColumn.setCellValueFactory(v -> string(v.getValue().getStatus()));
        executionCodeColumn.setCellValueFactory(v -> string(v.getValue().getExecutionCode()));
        executionExamColumn.setCellValueFactory(v -> string(v.getValue().getExamTitle()));
        executionCourseColumn.setCellValueFactory(v -> string(v.getValue().getCourseName()));
        executionStatusColumn.setCellValueFactory(v -> string(v.getValue().getStatus()));
        executionWindowColumn.setCellValueFactory(v -> string(
                time(v.getValue().getOpeningTime()) + " — "
                        + time(v.getValue().getClosingTime())));
        studentColumn.setCellValueFactory(v -> string(v.getValue().getStudentName()));
        submissionStatusColumn.setCellValueFactory(v -> string(v.getValue().getStatus()));
        finalScoreColumn.setCellValueFactory(v -> string(decimal(v.getValue().getFinalScore())));
    }

    private void clearQuestionDetail() {
        questionDetailArea.setText("Select a question to inspect its exact version history.");
        questionOptionsList.getItems().clear();
        if (questionIllustrationRenderer != null) questionIllustrationRenderer.render(null);
    }

    private void clearExamDetail() {
        examDetailArea.setText("Select an exam to inspect its exact version history.");
        examQuestionsList.getItems().clear();
        currentExamQuestions = List.of();
        if (examIllustrationRenderer != null) examIllustrationRenderer.render(null);
    }

    private void clearSubmissionDetail() {
        submissionDetailArea.setText("Select an execution and submission to inspect its result.");
        submissionAnswersList.getItems().clear();
        currentSubmissionAnswers = List.of();
        if (submissionIllustrationRenderer != null) {
            submissionIllustrationRenderer.render(null);
        }
    }

    private void showError(String message) {
        boolean visible = message != null && !message.isBlank();
        errorLabel.setText(visible ? message : "");
        errorLabel.setVisible(visible);
        errorLabel.setManaged(visible);
    }

    private static ReadOnlyStringWrapper string(Object value) {
        return new ReadOnlyStringWrapper(value == null ? "N/A" : value.toString());
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private static String time(LocalDateTime value) {
        return value == null ? "N/A" : DATE_TIME.format(value);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "N/A" : value.toPlainString();
    }
}
