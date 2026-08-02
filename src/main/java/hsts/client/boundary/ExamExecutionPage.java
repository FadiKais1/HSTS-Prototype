package hsts.client.boundary;

import hsts.client.control.ExamExecutionClientController;
import hsts.client.net.Client;
import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExecutionCodePayload;
import hsts.common.LoginResult;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.StartExamPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.StudentExamQuestionDTO;
import hsts.common.SubmissionIdPayload;
import hsts.common.type.SubmissionStatus;
import hsts.common.type.UserRole;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.User;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;

public class ExamExecutionPage {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String NO_ACTIVE_ATTEMPT_RESPONSE =
            "Invalid active-attempt response from server";

    private Client client;
    private User currentUser;
    private ExamSubmission currentSubmission;
    private int remainingTime;

    // COMPATIBILITY-ONLY: JavaFX state for the authenticated Assignment 3 flow.
    private Stage stage;
    private LoginResult loginResult;
    private Runnable backHandler;
    private ExamExecutionClientController executionController;
    private ExamExecutionPreviewDTO validatedPreview;
    private String validatedExecutionCode;
    private String validationRequestCode;
    private int submissionId;
    private LocalDateTime deadline;
    private SubmissionStatus submissionStatus;
    private List<StudentExamQuestionDTO> questions = List.of();
    private final ConfirmedAnswerState answerState = new ConfirmedAnswerState();
    private int currentQuestionIndex = -1;
    private Timeline countdown;
    private boolean renderingSelection;
    private boolean closed;
    private boolean validating;
    private boolean starting;
    private boolean reloading;
    private boolean submitting;
    private boolean deadlineActionTriggered;
    private boolean currentQuestionOptionsValid;
    private long lifecycleGeneration;
    private long validationGeneration;
    private long startGeneration;
    private long reloadGeneration;
    private long submissionGeneration;
    private long attemptGeneration;
    private long stateRevision;
    private QuestionIllustrationRenderer illustrationRenderer;

    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Button backButton;
    @FXML private VBox entryPane;
    @FXML private TextField executionCodeField;
    @FXML private Button validateButton;
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Label feedbackLabel;
    @FXML private VBox previewPane;
    @FXML private Label previewExamTitleLabel;
    @FXML private Label previewCourseLabel;
    @FXML private Label previewWindowLabel;
    @FXML private Label previewDurationLabel;
    @FXML private Label previewStatusLabel;
    @FXML private PasswordField identityField;
    @FXML private Button startButton;
    @FXML private VBox attemptPane;
    @FXML private Label examTitleLabel;
    @FXML private Label attemptDetailsLabel;
    @FXML private Label instructionsLabel;
    @FXML private Label submissionStatusLabel;
    @FXML private Label countdownLabel;
    @FXML private Label progressLabel;
    @FXML private ListView<String> questionListView;
    @FXML private Label questionPositionLabel;
    @FXML private Label questionTextLabel;
    @FXML private Label questionDetailsLabel;
    @FXML private RadioButton option1Radio;
    @FXML private RadioButton option2Radio;
    @FXML private RadioButton option3Radio;
    @FXML private RadioButton option4Radio;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Button refreshButton;
    @FXML private Button submitButton;
    @FXML private Label saveStatusLabel;
    @FXML private VBox questionIllustrationContainer;
    @FXML private ImageView questionIllustrationView;
    @FXML private Label questionIllustrationErrorLabel;

    private final ToggleGroup answerGroup = new ToggleGroup();

    @FXML
    private void initialize() {
        configureAnswerOptions();
        illustrationRenderer = new QuestionIllustrationRenderer(
                questionIllustrationView, questionIllustrationErrorLabel,
                questionIllustrationContainer
        );
        questionListView.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldIndex, newIndex) -> {
                    if (newIndex != null && newIndex.intValue() >= 0) {
                        showQuestion(newIndex.intValue());
                    }
                }
        );
        executionCodeField.textProperty().addListener((observable, oldCode, newCode) -> {
            String normalizedCode = normalizeExecutionCode(newCode);
            boolean invalidatesPreview = validatedPreview != null
                    && !normalizedCode.equals(validatedExecutionCode);
            boolean invalidatesPendingRequest = validating
                    && !normalizedCode.equals(validationRequestCode);
            if (invalidatesPreview || invalidatesPendingRequest) {
                validationGeneration++;
                startGeneration++;
                validating = false;
                validationRequestCode = null;
                clearPreview();
                setFeedback("Validate the updated execution code.");
                updateActionState();
            }
        });
        showPreview(null);
        showAttemptPane(false);
        setFeedback("Enter the four-character execution code.");
        setSaveStatus("");
        updateActionState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable backHandler) {
        stopCountdown();
        invalidateCallbacks();

        this.stage = Objects.requireNonNull(stage, "stage");
        this.client = Objects.requireNonNull(client, "client");
        this.loginResult = Objects.requireNonNull(loginResult, "loginResult");
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        if (loginResult.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Exam access is available only to students");
        }

        this.executionController = new ExamExecutionClientController(client);
        // Live server updates remove the need for a user-initiated refresh.
        client.setServerEventListener(this::onServerEvent);
        this.closed = false;
        this.validating = false;
        this.starting = false;
        this.reloading = false;
        this.submitting = false;
        this.deadlineActionTriggered = false;
        this.currentQuestionOptionsValid = false;
        this.validatedPreview = null;
        this.validatedExecutionCode = null;
        this.validationRequestCode = null;
        this.submissionId = 0;
        this.deadline = null;
        this.submissionStatus = null;
        this.questions = List.of();
        this.currentQuestionIndex = -1;
        this.remainingTime = 0;
        this.answerState.reset(Map.of());
        this.lifecycleGeneration++;
        this.attemptGeneration++;
        this.stateRevision++;

        userLabel.setText(safe(loginResult.getFullName()));
        roleLabel.setText(loginResult.getRole().name());
        executionCodeField.clear();
        clearIdentityInput();
        showPreview(null);
        showAttemptPane(false);
        setFeedback("Enter the four-character execution code.");
        setSaveStatus("");
        updateActionState();
    }

    private void configureAnswerOptions() {
        List<RadioButton> buttons = answerButtons();
        for (int index = 0; index < buttons.size(); index++) {
            RadioButton button = buttons.get(index);
            button.setToggleGroup(answerGroup);
            button.setUserData(index + 1);
        }
        answerGroup.selectedToggleProperty().addListener(
                (observable, oldSelection, newSelection) -> {
                    if (renderingSelection || newSelection == null) {
                        return;
                    }
                    Object userData = newSelection.getUserData();
                    if (userData instanceof Integer optionNumber) {
                        saveSelectedAnswer(optionNumber);
                    }
                }
        );
    }

    @FXML
    private void handleValidateCode() {
        if (!isConfigured() || validating || starting) {
            return;
        }

        String normalizedCode = normalizeExecutionCode(executionCodeField.getText());
        long generation = ++validationGeneration;
        startGeneration++;
        validating = true;
        validationRequestCode = normalizedCode;
        clearPreview();
        setFeedback("Validating execution code...");
        updateActionState();

        ExecutionCodePayload payload = buildExecutionCodePayload(normalizedCode);
        long lifecycle = lifecycleGeneration;
        executionController.validateExecutionCode(payload).whenComplete((preview, error) ->
                Platform.runLater(() -> {
                    if (isStale(closed, lifecycle, lifecycleGeneration,
                            generation, validationGeneration)) {
                        return;
                    }
                    validating = false;
                    validationRequestCode = null;
                    if (error != null || preview == null) {
                        clearPreview();
                        setFeedback(error == null
                                ? "Unable to validate execution code"
                                : cleanError(error));
                        updateActionState();
                        return;
                    }

                    validatedPreview = preview;
                    validatedExecutionCode = normalizedCode;
                    showPreview(preview);
                    setFeedback(preview.isResumable()
                            ? "Execution validated. Confirm identity to resume."
                            : "Execution validated. Confirm identity to start.");
                    updateActionState();
                })
        );
    }

    @FXML
    private void handleStartExam() {
        if (!isConfigured() || starting || validating || validatedPreview == null) {
            return;
        }

        StartExamPayload payload = buildStartPayload(
                validatedPreview,
                identityField.getText()
        );
        long generation = ++startGeneration;
        long lifecycle = lifecycleGeneration;
        starting = true;
        setFeedback(validatedPreview.isResumable()
                ? "Resuming exam attempt..."
                : "Starting exam attempt...");
        updateActionState();

        executionController.startExamAttempt(payload).whenComplete((attempt, error) ->
                Platform.runLater(() -> {
                    clearIdentityInput();
                    if (isStale(closed, lifecycle, lifecycleGeneration,
                            generation, startGeneration)) {
                        return;
                    }
                    starting = false;
                    if (error != null || attempt == null) {
                        setFeedback(error == null
                                ? "Unable to start exam attempt"
                                : cleanError(error));
                        updateActionState();
                        return;
                    }

                    if (applyAttempt(attempt)) {
                        setFeedback("Exam attempt loaded.");
                    }
                })
        );
    }

    @FXML
    private void handlePreviousQuestion() {
        if (currentQuestionIndex > 0) {
            questionListView.getSelectionModel().select(currentQuestionIndex - 1);
        }
    }

    @FXML
    private void handleNextQuestion() {
        if (currentQuestionIndex >= 0
                && currentQuestionIndex < questions.size() - 1) {
            questionListView.getSelectionModel().select(currentQuestionIndex + 1);
        }
    }

    @FXML
    private void handleRefreshAttempt() {
        refreshAttempt(false);
    }

    @FXML
    private void handleSubmitExam() {
        if (!isAttemptEditable() || submitting || answerState.hasPending()) {
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(stage);
        confirmation.setTitle("Submit exam");
        confirmation.setHeaderText("Submit this exam now?");
        confirmation.setContentText(
                "After submission, your answers become read-only."
        );
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        submitCurrentAttempt(false);
    }

    @FXML
    private void handleBack() {
        if (closed || backHandler == null) {
            return;
        }
        if (submissionStatus == SubmissionStatus.IN_PROGRESS) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.initOwner(stage);
            confirmation.setTitle("Leave exam page");
            confirmation.setHeaderText("Your exam attempt is still in progress");
            confirmation.setContentText(
                    "Leave this page without submitting? Saved answers remain on the server."
            );
            if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
                return;
            }
        }

        Runnable navigation = backHandler;
        closed = true;
        stopCountdown();
        invalidateCallbacks();
        backHandler = null;
        executionController = null;
        if (client != null) {
            client.setServerEventListener(null);
        }
        client = null;
        loginResult = null;
        stage = null;
        illustrationRenderer.dispose();
        navigation.run();
    }

    private void saveSelectedAnswer(int optionNumber) {
        if (!isAttemptEditable() || currentQuestionIndex < 0
                || currentQuestionIndex >= questions.size()) {
            renderConfirmedSelection();
            return;
        }

        StudentExamQuestionDTO question = questions.get(currentQuestionIndex);
        int questionId = question.getQuestionId();
        if (Objects.equals(answerState.confirmed(questionId), optionNumber)) {
            return;
        }
        long saveGeneration = answerState.begin(questionId);
        if (saveGeneration < 0) {
            renderConfirmedSelection();
            return;
        }

        SaveExamAnswerPayload payload = buildSavePayload(
                submissionId,
                question,
                optionNumber
        );
        long lifecycle = lifecycleGeneration;
        long attempt = attemptGeneration;
        setSaveStatus("Saving answer...");
        refreshQuestionNavigation();
        updateActionState();

        executionController.saveExamAnswer(payload).whenComplete((savedAnswer, error) ->
                Platform.runLater(() -> {
                    if (closed || lifecycle != lifecycleGeneration
                            || attempt != attemptGeneration
                            || !answerState.isCurrent(questionId, saveGeneration)) {
                        return;
                    }
                    if (error != null || !isMatchingAnswer(savedAnswer, questionId, optionNumber)) {
                        answerState.fail(questionId, saveGeneration);
                        if (currentQuestionId() == questionId) {
                            renderConfirmedSelection();
                        }
                        setFeedback(error == null
                                ? "Unable to confirm saved answer"
                                : cleanError(error));
                        setSaveStatus("Answer not saved. Select an option to retry.");
                        refreshQuestionNavigation();
                        updateActionState();
                        return;
                    }

                    answerState.confirm(questionId, optionNumber, saveGeneration);
                    stateRevision++;
                    setSaveStatus("Answer saved.");
                    refreshQuestionNavigation();
                    updateActionState();
                })
        );
    }

    /**
     * Reacts to state changes pushed by the server.
     *
     * <p>Invoked on the transport thread, so the work is marshalled onto the
     * JavaFX application thread. This is what keeps the countdown current when
     * a teacher grants extra time, without the student refreshing anything.</p>
     */
    private void onServerEvent(ServerEvent event) {
        if (event == null || event.getType() != ServerEventType.EXAM_TIME_EXTENDED) {
            return;
        }

        Platform.runLater(() -> {
            if (closed || !isConfigured() || submissionId <= 0) {
                return;
            }
            if (submissionStatus != SubmissionStatus.IN_PROGRESS) {
                return;
            }
            setFeedback("Your teacher updated the exam time. The remaining time was refreshed.");
            refreshAttempt(false);
        });
    }

    private void refreshAttempt(boolean finalizationReconcile) {
        if (!isConfigured() || submissionId <= 0 || reloading || submitting
                || answerState.hasPending()) {
            return;
        }

        long generation = ++reloadGeneration;
        long lifecycle = lifecycleGeneration;
        long attempt = attemptGeneration;
        long revision = stateRevision;
        reloading = true;
        setFeedback("Refreshing exam attempt...");
        updateActionState();

        executionController.getActiveExamAttempt(
                new SubmissionIdPayload(submissionId)
        ).whenComplete((loadedAttempt, error) -> Platform.runLater(() -> {
            if (closed || lifecycle != lifecycleGeneration
                    || generation != reloadGeneration
                    || attempt != attemptGeneration
                    || revision != stateRevision) {
                return;
            }
            reloading = false;
            if (loadedAttempt == null || isNoActiveAttempt(error)) {
                markNoActiveAttempt();
                setFeedback("No active attempt");
                return;
            }
            if (error != null) {
                setFeedback(cleanError(error));
                if (finalizationReconcile) {
                    setSaveStatus("Submission status could not be refreshed.");
                }
                updateActionState();
                return;
            }

            if (applyAttempt(loadedAttempt)) {
                setFeedback("Exam attempt refreshed.");
            }
        }));
    }

    private void submitCurrentAttempt(boolean deadlineTriggered) {
        if (!isConfigured() || submissionId <= 0 || submitting
                || submissionStatus != SubmissionStatus.IN_PROGRESS) {
            return;
        }

        long generation = ++submissionGeneration;
        long lifecycle = lifecycleGeneration;
        long attempt = attemptGeneration;
        submitting = true;
        stopCountdown();
        setFeedback(deadlineTriggered
                ? "Time expired. Finalizing exam..."
                : "Submitting exam...");
        updateActionState();

        executionController.submitExamAttempt(
                new SubmissionIdPayload(submissionId)
        ).whenComplete((submittedAttempt, error) -> Platform.runLater(() -> {
            if (closed || lifecycle != lifecycleGeneration
                    || generation != submissionGeneration
                    || attempt != attemptGeneration) {
                return;
            }
            submitting = false;
            if (error != null || submittedAttempt == null) {
                setFeedback(error == null
                        ? "Unable to submit exam"
                        : cleanError(error));
                setSaveStatus("Refresh to reconcile the exam status.");
                updateActionState();
                if (deadlineTriggered) {
                    refreshAttempt(true);
                } else if (isAttemptEditable()) {
                    startCountdown();
                }
                return;
            }

            if (applyAttempt(submittedAttempt)) {
                setFeedback("Exam submitted successfully.");
            }
        }));
    }

    private boolean applyAttempt(ExamAttemptDTO attempt) {
        Objects.requireNonNull(attempt, "attempt");
        stopCountdown();
        List<StudentExamQuestionDTO> loadedQuestions =
                preserveQuestionOrder(attempt.getQuestions());
        try {
            for (StudentExamQuestionDTO question : loadedQuestions) {
                optionTexts(question);
            }
        } catch (IllegalArgumentException exception) {
            currentQuestionOptionsValid = false;
            showAttemptPane(false);
            setFeedback(exception.getMessage());
            updateActionState();
            return false;
        }
        attemptGeneration++;
        reloadGeneration++;
        submissionId = attempt.getSubmissionId();
        deadline = attempt.getDeadline();
        submissionStatus = attempt.getStatus();
        questions = loadedQuestions;
        answerState.reset(restoreSelections(attempt.getAnswers()));
        currentQuestionIndex = questions.isEmpty() ? -1 : 0;
        stateRevision++;
        deadlineActionTriggered = submissionStatus != SubmissionStatus.IN_PROGRESS;

        examTitleLabel.setText(safe(attempt.getExamTitle()));
        attemptDetailsLabel.setText(
                "Execution " + safe(attempt.getExecutionCode())
                        + "  |  Version " + attempt.getExamVersionNo()
        );
        instructionsLabel.setText(safe(attempt.getStudentInstructions()));
        submissionStatusLabel.setText(statusText(submissionStatus));
        showAttemptPane(true);
        refreshQuestionNavigation();
        if (!questions.isEmpty()) {
            questionListView.getSelectionModel().select(0);
            showQuestion(0);
        } else {
            clearQuestionDisplay();
        }
        setSaveStatus(submissionStatus == SubmissionStatus.IN_PROGRESS
                ? "Answers are saved individually."
                : "This attempt is read-only.");
        startCountdown();
        updateActionState();
        return true;
    }

    private void markNoActiveAttempt() {
        stopCountdown();
        attemptGeneration++;
        submissionStatus = null;
        deadline = null;
        remainingTime = 0;
        countdownLabel.setText("00:00:00");
        submissionStatusLabel.setText("NO ACTIVE ATTEMPT");
        setSaveStatus("The server reports no active attempt.");
        updateActionState();
    }

    private void showQuestion(int index) {
        if (index < 0 || index >= questions.size()) {
            clearQuestionDisplay();
            return;
        }
        currentQuestionIndex = index;
        StudentExamQuestionDTO question = questions.get(index);
        questionPositionLabel.setText(
                "Question " + (index + 1) + " of " + questions.size()
        );
        questionTextLabel.setText(safe(question.getContent()));
        questionDetailsLabel.setText(
                safe(question.getTopic()) + "  |  " + safe(question.getDifficulty())
        );
        illustrationRenderer.render(question.getIllustration());
        List<RadioButton> buttons = answerButtons();
        List<String> options;
        try {
            options = optionTexts(question);
        } catch (IllegalArgumentException exception) {
            currentQuestionOptionsValid = false;
            renderingSelection = true;
            try {
                answerGroup.selectToggle(null);
                for (RadioButton button : buttons) {
                    button.setText("");
                    button.setVisible(false);
                    button.setManaged(false);
                }
            } finally {
                renderingSelection = false;
            }
            setFeedback(exception.getMessage());
            updateActionState();
            return;
        }
        currentQuestionOptionsValid = true;
        for (int optionIndex = 0; optionIndex < buttons.size(); optionIndex++) {
            RadioButton button = buttons.get(optionIndex);
            button.setText(options.get(optionIndex));
            button.setUserData(optionIndex + 1);
            button.setVisible(true);
            button.setManaged(true);
        }
        renderConfirmedSelection();
        updateActionState();
    }

    private void renderConfirmedSelection() {
        renderingSelection = true;
        try {
            answerGroup.selectToggle(null);
            Integer selected = answerState.confirmed(currentQuestionId());
            if (selected != null && selected >= 1 && selected <= 4) {
                answerGroup.selectToggle(answerButtons().get(selected - 1));
            }
        } finally {
            renderingSelection = false;
        }
    }

    private void refreshQuestionNavigation() {
        int selectedIndex = currentQuestionIndex;
        List<String> labels = questionNavigationLabels(
                questions,
                answerState.confirmedAnswers(),
                answerState.pendingQuestions()
        );
        questionListView.getItems().setAll(labels);
        if (selectedIndex >= 0 && selectedIndex < labels.size()) {
            questionListView.getSelectionModel().select(selectedIndex);
        }
        progressLabel.setText(
                answeredCount(questions, answerState.confirmedAnswers())
                        + " of " + questions.size() + " answered"
        );
    }

    private void clearQuestionDisplay() {
        currentQuestionIndex = -1;
        currentQuestionOptionsValid = false;
        questionPositionLabel.setText("No questions available");
        questionTextLabel.setText("");
        questionDetailsLabel.setText("");
        illustrationRenderer.render(null);
        renderingSelection = true;
        try {
            answerGroup.selectToggle(null);
            for (RadioButton button : answerButtons()) {
                button.setText("");
            }
        } finally {
            renderingSelection = false;
        }
        progressLabel.setText("0 of 0 answered");
    }

    private void startCountdown() {
        stopCountdown();
        if (submissionStatus != SubmissionStatus.IN_PROGRESS || deadline == null) {
            remainingTime = 0;
            countdownLabel.setText("00:00:00");
            updateActionState();
            return;
        }
        updateCountdown();
        if (remainingTime <= 0) {
            return;
        }
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateCountdown()));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

    private void updateCountdown() {
        long seconds = remainingSeconds(deadline, LocalDateTime.now());
        remainingTime = (int) Math.min(Integer.MAX_VALUE, seconds);
        countdownLabel.setText(formatRemainingTime(seconds));
        if (seconds == 0 && submissionStatus == SubmissionStatus.IN_PROGRESS) {
            stopCountdown();
            updateActionState();
            if (!deadlineActionTriggered) {
                deadlineActionTriggered = true;
                submitCurrentAttempt(true);
            }
        }
    }

    private void clearPreview() {
        validatedPreview = null;
        validatedExecutionCode = null;
        showPreview(null);
    }

    private void showPreview(ExamExecutionPreviewDTO preview) {
        boolean visible = preview != null;
        previewPane.setVisible(visible);
        previewPane.setManaged(visible);
        if (!visible) {
            previewExamTitleLabel.setText("");
            previewCourseLabel.setText("");
            previewWindowLabel.setText("");
            previewDurationLabel.setText("");
            previewStatusLabel.setText("");
            return;
        }
        previewExamTitleLabel.setText(safe(preview.getExamTitle()));
        previewCourseLabel.setText(safe(preview.getCourseName()));
        // Spelling out which time is which avoids the reader having to infer it
        // from the order of two similar looking timestamps.
        previewWindowLabel.setText(
                "Starts " + formatDateTime(preview.getOpeningTime())
                        + "     Ends " + formatDateTime(preview.getClosingTime())
        );
        previewDurationLabel.setText(preview.getDurationMinutes() + " minutes");
        previewStatusLabel.setText(
                (preview.getStatus() == null ? "" : preview.getStatus().name())
                        + (preview.isResumable() ? "  |  RESUMABLE" : "")
        );
    }

    private void showAttemptPane(boolean visible) {
        attemptPane.setVisible(visible);
        attemptPane.setManaged(visible);
        entryPane.setVisible(!visible);
        entryPane.setManaged(!visible);
    }

    private void updateActionState() {
        boolean configured = isConfigured();
        boolean requestBusy = validating || starting || reloading || submitting;
        boolean editable = isAttemptEditable();
        int currentQuestionId = currentQuestionId();
        boolean currentSavePending = answerState.isPending(currentQuestionId);

        validateButton.setDisable(!configured || requestBusy);
        executionCodeField.setDisable(!configured || requestBusy);
        identityField.setDisable(!configured || validatedPreview == null || requestBusy);
        startButton.setDisable(!configured || validatedPreview == null || requestBusy);
        backButton.setDisable(!configured || starting || submitting);
        refreshButton.setDisable(!configured || submissionId <= 0 || requestBusy
                || answerState.hasPending());
        submitButton.setDisable(!editable || requestBusy || answerState.hasPending());
        previousButton.setDisable(currentQuestionIndex <= 0);
        nextButton.setDisable(currentQuestionIndex < 0
                || currentQuestionIndex >= questions.size() - 1);
        for (RadioButton button : answerButtons()) {
            button.setDisable(!currentQuestionOptionsValid || !editable
                    || currentSavePending || requestBusy);
        }
        boolean busy = requestBusy || answerState.hasPending();
        busyIndicator.setVisible(busy);
        busyIndicator.setManaged(busy);
    }

    private boolean isConfigured() {
        return !closed && client != null && executionController != null
                && loginResult != null;
    }

    private boolean isAttemptEditable() {
        return isEditable(submissionStatus, remainingTime)
                && !deadlineActionTriggered;
    }

    private int currentQuestionId() {
        if (currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) {
            return -1;
        }
        return questions.get(currentQuestionIndex).getQuestionId();
    }

    private List<RadioButton> answerButtons() {
        return List.of(option1Radio, option2Radio, option3Radio, option4Radio);
    }

    private void clearIdentityInput() {
        if (identityField != null) {
            identityField.clear();
        }
    }

    private void invalidateCallbacks() {
        validationGeneration++;
        startGeneration++;
        reloadGeneration++;
        submissionGeneration++;
        attemptGeneration++;
        stateRevision++;
    }

    private void setFeedback(String message) {
        feedbackLabel.setText(message == null || message.isBlank()
                ? "Unable to complete the request"
                : message);
    }

    private void setSaveStatus(String message) {
        saveStatusLabel.setText(message == null ? "" : message);
    }

    public void enterExecutionCode(String code) {
        executionCodeField.setText(code == null ? "" : code);
        handleValidateCode();
    }

    public void startExam(String StudentId) {
        identityField.setText(StudentId == null ? "" : StudentId);
        handleStartExam();
    }

    public void showRemainingTime() {
        updateCountdown();
    }

    public void submitExam() {
        handleSubmitExam();
    }

    public void showSubMessage() {
        setFeedback(submissionStatus == null
                ? "No active attempt"
                : "Submission status: " + submissionStatus.name());
    }

    public void extendStudentTime(int minutes, int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void selectAnswer(int questionId, int answerNumber) {
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).getQuestionId() == questionId) {
                questionListView.getSelectionModel().select(index);
                if (answerNumber >= 1 && answerNumber <= 4) {
                    answerGroup.selectToggle(answerButtons().get(answerNumber - 1));
                }
                return;
            }
        }
    }

    static String normalizeExecutionCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    static ExecutionCodePayload buildExecutionCodePayload(String code) {
        return new ExecutionCodePayload(normalizeExecutionCode(code));
    }

    static StartExamPayload buildStartPayload(ExamExecutionPreviewDTO preview,
                                               String identityConfirmation) {
        return new StartExamPayload(
                Objects.requireNonNull(preview, "preview").getExecutionId(),
                identityConfirmation
        );
    }

    static SaveExamAnswerPayload buildSavePayload(int submissionId,
                                                   StudentExamQuestionDTO question,
                                                   int selectedOptionNumber) {
        return new SaveExamAnswerPayload(
                submissionId,
                Objects.requireNonNull(question, "question").getQuestionId(),
                selectedOptionNumber
        );
    }

    static List<StudentExamQuestionDTO> preserveQuestionOrder(
            List<StudentExamQuestionDTO> serverQuestions) {
        return List.copyOf(Objects.requireNonNull(serverQuestions, "serverQuestions"));
    }

    static List<String> optionTexts(StudentExamQuestionDTO question) {
        Objects.requireNonNull(question, "question");
        return List.of(
                requireOptionText(question.getAnswerOption1()),
                requireOptionText(question.getAnswerOption2()),
                requireOptionText(question.getAnswerOption3()),
                requireOptionText(question.getAnswerOption4())
        );
    }

    private static String requireOptionText(String optionText) {
        if (optionText == null || optionText.isBlank()) {
            throw new IllegalArgumentException(
                    "Question answer options are unavailable"
            );
        }
        return optionText;
    }

    static Map<Integer, Integer> restoreSelections(List<StudentAnswerDTO> answers) {
        Map<Integer, Integer> selections = new LinkedHashMap<>();
        for (StudentAnswerDTO answer : Objects.requireNonNull(answers, "answers")) {
            if (answer != null && answer.getSelectedOptionNumber() != null) {
                selections.put(answer.getQuestionId(), answer.getSelectedOptionNumber());
            }
        }
        return Map.copyOf(selections);
    }

    static int answeredCount(List<StudentExamQuestionDTO> questions,
                             Map<Integer, Integer> confirmedAnswers) {
        int count = 0;
        for (StudentExamQuestionDTO question : questions) {
            if (confirmedAnswers.containsKey(question.getQuestionId())) {
                count++;
            }
        }
        return count;
    }

    static List<String> questionNavigationLabels(
            List<StudentExamQuestionDTO> questions,
            Map<Integer, Integer> confirmedAnswers,
            Set<Integer> pendingQuestions) {
        List<String> labels = new ArrayList<>(questions.size());
        for (int index = 0; index < questions.size(); index++) {
            int questionId = questions.get(index).getQuestionId();
            String state = pendingQuestions.contains(questionId)
                    ? "Saving"
                    : confirmedAnswers.containsKey(questionId)
                    ? "Answered"
                    : "Unanswered";
            labels.add("Question " + (index + 1) + " - " + state);
        }
        return List.copyOf(labels);
    }

    static long remainingSeconds(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null || now == null) {
            return 0;
        }
        long millis = java.time.Duration.between(now, deadline).toMillis();
        return millis <= 0 ? 0 : (millis + 999) / 1000;
    }

    static String formatRemainingTime(long seconds) {
        long safeSeconds = Math.max(0, seconds);
        long hours = safeSeconds / 3600;
        long minutes = (safeSeconds % 3600) / 60;
        long remainingSeconds = safeSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d",
                hours, minutes, remainingSeconds);
    }

    static boolean isEditable(SubmissionStatus status, int remainingTime) {
        return status == SubmissionStatus.IN_PROGRESS && remainingTime > 0;
    }

    static boolean isStale(boolean closed, long lifecycle, long currentLifecycle,
                           long responseGeneration, long currentGeneration) {
        return closed || lifecycle != currentLifecycle
                || responseGeneration != currentGeneration;
    }

    static String cleanError(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current.getCause() != null)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank()
                ? "Unable to complete the request"
                : message;
    }

    static boolean isNoActiveAttempt(Throwable error) {
        return error != null && NO_ACTIVE_ATTEMPT_RESPONSE.equals(cleanError(error));
    }

    private static boolean isMatchingAnswer(StudentAnswerDTO answer,
                                            int questionId,
                                            int selectedOptionNumber) {
        return answer != null
                && answer.getQuestionId() == questionId
                && Objects.equals(answer.getSelectedOptionNumber(), selectedOptionNumber);
    }

    private static String statusText(SubmissionStatus status) {
        return status == null ? "UNKNOWN" : status.name();
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class ConfirmedAnswerState {
        private final Map<Integer, Integer> confirmedAnswers = new HashMap<>();
        private final Map<Integer, Long> pendingGenerations = new HashMap<>();
        private long nextGeneration;

        void reset(Map<Integer, Integer> restoredAnswers) {
            confirmedAnswers.clear();
            confirmedAnswers.putAll(restoredAnswers);
            pendingGenerations.clear();
            nextGeneration++;
        }

        long begin(int questionId) {
            if (pendingGenerations.containsKey(questionId)) {
                return -1;
            }
            long generation = ++nextGeneration;
            pendingGenerations.put(questionId, generation);
            return generation;
        }

        boolean confirm(int questionId, int optionNumber, long generation) {
            if (!isCurrent(questionId, generation)) {
                return false;
            }
            pendingGenerations.remove(questionId);
            confirmedAnswers.put(questionId, optionNumber);
            return true;
        }

        Integer fail(int questionId, long generation) {
            if (isCurrent(questionId, generation)) {
                pendingGenerations.remove(questionId);
            }
            return confirmedAnswers.get(questionId);
        }

        boolean isCurrent(int questionId, long generation) {
            return Objects.equals(pendingGenerations.get(questionId), generation);
        }

        boolean isPending(int questionId) {
            return pendingGenerations.containsKey(questionId);
        }

        boolean hasPending() {
            return !pendingGenerations.isEmpty();
        }

        Integer confirmed(int questionId) {
            return confirmedAnswers.get(questionId);
        }

        Map<Integer, Integer> confirmedAnswers() {
            return Map.copyOf(confirmedAnswers);
        }

        Set<Integer> pendingQuestions() {
            return Set.copyOf(new HashSet<>(pendingGenerations.keySet()));
        }
    }
}
