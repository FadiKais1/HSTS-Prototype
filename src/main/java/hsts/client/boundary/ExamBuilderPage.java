package hsts.client.boundary;

import hsts.client.control.ExamClientController;
import hsts.client.control.QuestionClientController;
import hsts.client.net.Client;
import hsts.common.CourseSummaryDTO;
import hsts.common.CreateExamPayload;
import hsts.common.ExamDTO;
import hsts.common.ExamQuestionDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamSummaryDTO;
import hsts.common.ExamVersionPayload;
import hsts.common.GenerateExamPayload;
import hsts.common.QuestionDTO;
import hsts.common.QuestionIllustrationDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.UpdateExamPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.server.entity.Course;
import hsts.server.entity.Exam;
import hsts.server.entity.Teacher;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ExamBuilderPage {
    private Client client;
    private Teacher currentTeacher;
    private Course selectedCourse;
    private List selectedQuestions;
    private Exam exam;

    // COMPATIBILITY-ONLY: JavaFX state for the Assignment 3 manual exam workflow.
    private Stage stage;
    private Runnable backHandler;
    private Runnable questionBankHandler;
    private ExamClientController examClientController;
    private QuestionClientController questionClientController;
    private ExamDTO loadedExam;
    private boolean closed;
    private boolean coursesLoading;
    private boolean examsLoading;
    private boolean questionsLoading;
    private boolean examLoading;
    private boolean savePending;
    private boolean submitPending;
    private boolean generationPending;
    private long courseRequestGeneration;
    private long summaryRequestGeneration;
    private long detailRequestGeneration;
    private long questionRequestGeneration;
    private long automaticRequestGeneration;
    private QuestionIllustrationRenderer illustrationRenderer;
    private EditorState pendingEditorState;
    private String baselineFingerprint;

    private final ObservableList<CourseSummaryDTO> assignedCourses =
            FXCollections.observableArrayList();
    private final ObservableList<ExamSummaryDTO> examSummaries =
            FXCollections.observableArrayList();
    private final ObservableList<QuestionDTO> availableQuestions =
            FXCollections.observableArrayList();
    private final ObservableList<SelectedQuestionItem> selectedQuestionItems =
            FXCollections.observableArrayList();

    @FXML private ComboBox<CourseSummaryDTO> courseComboBox;
    @FXML private ToggleGroup creationModeGroup;
    @FXML private RadioButton manualModeRadio;
    @FXML private RadioButton automaticModeRadio;
    @FXML private TableView<ExamSummaryDTO> examTable;
    @FXML private TableColumn<ExamSummaryDTO, String> examCodeColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> examTitleColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> examCourseColumn;
    @FXML private TableColumn<ExamSummaryDTO, Number> examVersionColumn;
    @FXML private TableColumn<ExamSummaryDTO, Number> examTotalColumn;
    @FXML private TableColumn<ExamSummaryDTO, String> examStatusColumn;
    @FXML private TextField titleField;
    @FXML private TextField durationField;
    @FXML private TextArea teacherNotesArea;
    @FXML private TextArea studentInstructionsArea;
    @FXML private VBox automaticCriteriaPane;
    @FXML private TextField topicField;
    @FXML private ComboBox<DifficultyLevel> difficultyComboBox;
    @FXML private TextField questionCountField;
    @FXML private Button generateButton;
    @FXML private SplitPane manualQuestionPane;
    @FXML private TableView<QuestionDTO> availableQuestionsTable;
    @FXML private TableColumn<QuestionDTO, Number> availableIdColumn;
    @FXML private TableColumn<QuestionDTO, String> availableContentColumn;
    @FXML private TableColumn<QuestionDTO, String> availableTopicColumn;
    @FXML private TableColumn<QuestionDTO, String> availableDifficultyColumn;
    @FXML private TableColumn<QuestionDTO, Number> availableVersionColumn;
    @FXML private TableView<SelectedQuestionItem> selectedQuestionsTable;
    @FXML private TableColumn<SelectedQuestionItem, Number> selectedOrderColumn;
    @FXML private TableColumn<SelectedQuestionItem, Number> selectedIdColumn;
    @FXML private TableColumn<SelectedQuestionItem, String> selectedContentColumn;
    @FXML private TableColumn<SelectedQuestionItem, Number> selectedVersionColumn;
    @FXML private TableColumn<SelectedQuestionItem, Double> selectedScoreColumn;
    @FXML private Label loadedExamLabel;
    @FXML private Label versionStatusLabel;
    @FXML private Label totalScoreLabel;
    @FXML private Label totalFeedbackLabel;
    @FXML private Label statusLabel;
    @FXML private Button newButton;
    @FXML private Button openButton;
    @FXML private Button saveButton;
    @FXML private Button submitButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private Button questionBankButton;
    @FXML private Button addButton;
    @FXML private Button removeButton;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;
    @FXML private VBox questionIllustrationContainer;
    @FXML private ImageView questionIllustrationView;
    @FXML private Label questionIllustrationErrorLabel;
    @FXML private VBox rejectionReasonPanel;
    @FXML private Label rejectionReasonLabel;

    @FXML
    private void initialize() {
        configureCourseDisplay();
        configureExamTable();
        configureQuestionTables();
        illustrationRenderer = new QuestionIllustrationRenderer(
                questionIllustrationView, questionIllustrationErrorLabel,
                questionIllustrationContainer
        );
        difficultyComboBox.setItems(FXCollections.observableArrayList(
                automaticDifficulties()
        ));
        courseComboBox.valueProperty().addListener((observable, oldCourse, newCourse) -> {
            if (newCourse != null && isConfigured() && !isAutomaticMode()) {
                loadActiveQuestions(newCourse.getCourseId());
            } else {
                questionRequestGeneration++;
                questionsLoading = false;
                availableQuestions.clear();
                updateActionState();
            }
        });
        examTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldExam, newExam) -> updateActionState()
        );
        availableQuestionsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldQuestion, newQuestion) -> {
                    illustrationRenderer.render(newQuestion == null
                            ? null : newQuestion.getIllustration());
                    updateActionState();
                }
        );
        selectedQuestionsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldQuestion, newQuestion) -> {
                    illustrationRenderer.render(newQuestion == null
                            ? null : newQuestion.getIllustration());
                    updateActionState();
                }
        );
        setStatus("Waiting for authenticated client.");
        updateTotal();
        updateModeVisibility();
        updateActionState();
    }

    public void configure(Stage stage, Client client, Runnable backHandler) {
        configure(stage, client, backHandler, null);
    }

    public void configure(Stage stage, Client client, Runnable backHandler,
                          Runnable questionBankHandler) {
        configure(stage, client, backHandler, questionBankHandler, null);
    }

    public void configure(Stage stage, Client client, Runnable backHandler,
                          Runnable questionBankHandler, EditorState editorState) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.client = Objects.requireNonNull(client, "client");
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        this.questionBankHandler = questionBankHandler;
        this.pendingEditorState = editorState;
        this.examClientController = new ExamClientController(client);
        this.questionClientController = new QuestionClientController(client);
        this.closed = false;
        this.generationPending = false;
        this.automaticRequestGeneration++;
        setStatus("Loading courses and exams...");
        updateActionState();
        loadAssignedCourses();
        loadExamSummaries(null, null);
    }

    private void configureCourseDisplay() {
        courseComboBox.setItems(assignedCourses);
        courseComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseSummaryDTO course) {
                if (course == null) {
                    return "";
                }
                return course.getCourseName() + " — " + course.getSubjectName()
                        + " (" + course.getCourseCode() + ")";
            }

            @Override
            public CourseSummaryDTO fromString(String value) {
                return null;
            }
        });
    }

    private void configureExamTable() {
        examTable.setItems(examSummaries);
        examTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        examCodeColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getExamCode())));
        examTitleColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getTitle())));
        examCourseColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getCourseName())));
        examVersionColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getVersionNo()));
        examTotalColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getTotalScore()));
        examStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus() == null ? "" : data.getValue().getStatus().name()
        ));
    }

    private void configureQuestionTables() {
        availableQuestionsTable.setItems(availableQuestions);
        availableQuestionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        availableIdColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuestionId()));
        availableContentColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getContent())));
        availableTopicColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getTopic())));
        availableDifficultyColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getDifficulty())));
        availableVersionColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getVersionNo()));

        selectedQuestionsTable.setItems(selectedQuestionItems);
        selectedQuestionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        selectedQuestionsTable.setEditable(true);
        selectedOrderColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getOrderNumber()));
        selectedIdColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuestionId()));
        selectedContentColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getContent())));
        selectedVersionColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuestionVersionNo()));
        selectedScoreColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getScore()).asObject());
        selectedScoreColumn.setCellFactory(
                TextFieldTableCell.forTableColumn(new DoubleStringConverter())
        );
        selectedScoreColumn.setOnEditCommit(event -> {
            double score = event.getNewValue() == null ? Double.NaN : event.getNewValue();
            if (!isEditable() || !Double.isFinite(score) || score <= 0) {
                setStatus("Question scores must be positive and finite.");
                selectedQuestionsTable.refresh();
                return;
            }
            event.getRowValue().setScore(score);
            updateTotal();
        });
    }

    private void loadAssignedCourses() {
        long generation = ++courseRequestGeneration;
        coursesLoading = true;
        updateActionState();
        questionClientController.getMyCourses().whenComplete((courses, error) ->
                Platform.runLater(() -> {
                    if (closed || generation != courseRequestGeneration) {
                        return;
                    }
                    coursesLoading = false;
                    if (error != null) {
                        setStatus(cleanError(error));
                        updateActionState();
                        return;
                    }
                    assignedCourses.setAll(courses);
                    if (pendingEditorState != null) {
                        EditorState state = pendingEditorState;
                        pendingEditorState = null;
                        restoreEditorState(state);
                        updateActionState();
                        return;
                    }
                    if (loadedExam != null) {
                        courseComboBox.setValue(findCourse(loadedExam.getCourseId()));
                    } else if (courseComboBox.getValue() == null
                            && !assignedCourses.isEmpty()) {
                        courseComboBox.setValue(assignedCourses.get(0));
                    }
                    if (assignedCourses.isEmpty()) {
                        setStatus("No assigned courses are available.");
                    }
                    if (baselineFingerprint == null) {
                        baselineFingerprint = editorFingerprint();
                    }
                    updateActionState();
                })
        );
    }

    private void loadExamSummaries(Integer selectExamId, String completionMessage) {
        long generation = ++summaryRequestGeneration;
        examsLoading = true;
        updateActionState();
        examClientController.getMyExams().whenComplete((summaries, error) ->
                Platform.runLater(() -> {
                    if (closed || generation != summaryRequestGeneration) {
                        return;
                    }
                    examsLoading = false;
                    if (error != null) {
                        setStatus(cleanError(error));
                        updateActionState();
                        return;
                    }
                    examSummaries.setAll(summaries);
                    selectExamSummary(selectExamId);
                    if (completionMessage != null) {
                        setStatus(completionMessage);
                    } else if (summaries.isEmpty()) {
                        setStatus("No exams have been created yet.");
                    } else if (!coursesLoading) {
                        setStatus("Courses and exams loaded.");
                    }
                    updateActionState();
                })
        );
    }

    private void loadActiveQuestions(int courseId) {
        if (questionClientController == null) {
            return;
        }
        long generation = ++questionRequestGeneration;
        questionsLoading = true;
        availableQuestions.clear();
        updateActionState();
        QuestionFilterPayload filter = new QuestionFilterPayload(
                courseId,
                null,
                null,
                null,
                QuestionStatus.ACTIVE
        );
        questionClientController.listQuestions(filter).whenComplete((questions, error) ->
                Platform.runLater(() -> {
                    if (closed || generation != questionRequestGeneration
                            || courseComboBox.getValue() == null
                            || courseComboBox.getValue().getCourseId() != courseId) {
                        return;
                    }
                    questionsLoading = false;
                    if (error != null) {
                        setStatus(cleanError(error));
                    } else {
                        availableQuestions.setAll(questions);
                        setStatus(questions.isEmpty()
                                ? "No ACTIVE questions are available for this course."
                                : "ACTIVE questions loaded.");
                    }
                    updateActionState();
                })
        );
    }

    @FXML
    private void handleNewExam() {
        if (savePending || submitPending || generationPending) {
            return;
        }
        if (!confirmDiscardIfDirty()) return;
        loadedExam = null;
        loadedExamLabel.setText("New DRAFT exam");
        versionStatusLabel.setText("Unsaved DRAFT");
        titleField.clear();
        durationField.clear();
        teacherNotesArea.clear();
        studentInstructionsArea.clear();
        selectedQuestionItems.clear();
        showRejectionReason(null);
        examTable.getSelectionModel().clearSelection();
        courseComboBox.setDisable(false);
        if (courseComboBox.getValue() == null && !assignedCourses.isEmpty()) {
            courseComboBox.setValue(assignedCourses.get(0));
        } else if (courseComboBox.getValue() != null) {
            loadActiveQuestions(courseComboBox.getValue().getCourseId());
        }
        updateTotal();
        setStatus(isAutomaticMode()
                ? "Complete the criteria and generate a new DRAFT exam."
                : "Complete the form and save the new DRAFT exam.");
        updateActionState();
        baselineFingerprint = editorFingerprint();
    }

    @FXML
    private void handleOpenExam() {
        ExamSummaryDTO selected = examTable.getSelectionModel().getSelectedItem();
        if (selected == null || examLoading) {
            setStatus("Select an exam to open.");
            return;
        }
        openExam(selected.getExamId(), null);
    }

    private void openExam(int examId, String completionMessage) {
        long generation = ++detailRequestGeneration;
        examLoading = true;
        updateActionState();
        examClientController.getMyExam(examId).whenComplete((loaded, error) ->
                Platform.runLater(() -> {
                    if (closed || generation != detailRequestGeneration) {
                        return;
                    }
                    examLoading = false;
                    if (error != null) {
                        setStatus(cleanError(error));
                    } else {
                        showExam(loaded);
                        setStatus(completionMessage == null
                                ? "Exam loaded."
                                : completionMessage);
                    }
                    updateActionState();
                })
        );
    }

    private void showExam(ExamDTO loaded) {
        selectManualMode();
        loadedExam = Objects.requireNonNull(loaded);
        loadedExamLabel.setText(safe(loaded.getExamCode()) + " — " + safe(loaded.getTitle()));
        versionStatusLabel.setText(
                "Version " + loaded.getVersionNo() + " • " + loaded.getStatus()
        );
        titleField.setText(safe(loaded.getTitle()));
        durationField.setText(String.valueOf(loaded.getDurationMinutes()));
        teacherNotesArea.setText(safe(loaded.getTeacherNotes()));
        studentInstructionsArea.setText(safe(loaded.getStudentInstructions()));
        CourseSummaryDTO course = findCourse(loaded.getCourseId());
        courseComboBox.setValue(course);
        courseComboBox.setDisable(true);

        List<ExamQuestionDTO> snapshots = new ArrayList<>(loaded.getQuestions());
        selectedQuestionItems.setAll(selectedItemsFromExam(snapshots));
        renumberSelectedQuestions();
        selectExamSummary(loaded.getExamId());
        updateTotal();
        updateEditabilityMessage();
        showRejectionReason(loaded);
        baselineFingerprint = editorFingerprint();
    }

    private void showRejectionReason(ExamDTO loaded) {
        boolean rejected = loaded != null && loaded.getStatus() == ExamStatus.REJECTED
                && loaded.getRejectionReason() != null
                && !loaded.getRejectionReason().isBlank();
        if (rejectionReasonPanel != null) {
            rejectionReasonPanel.setManaged(rejected);
            rejectionReasonPanel.setVisible(rejected);
        }
        if (rejectionReasonLabel != null) {
            rejectionReasonLabel.setText(rejected ? loaded.getRejectionReason() : "");
        }
    }

    private void updateEditabilityMessage() {
        if (loadedExam == null) {
            return;
        }
        if (loadedExam.getStatus() == ExamStatus.PENDING_APPROVAL) {
            versionStatusLabel.setText(
                    "Version " + loadedExam.getVersionNo() + " • PENDING_APPROVAL • Read-only"
            );
        } else if (loadedExam.getStatus() == ExamStatus.APPROVED
                || loadedExam.getStatus() == ExamStatus.REJECTED) {
            versionStatusLabel.setText(
                    "Version " + loadedExam.getVersionNo() + " • " + loadedExam.getStatus()
                            + " • Save creates a new DRAFT version requiring approval"
            );
        }
    }

    @FXML
    private void handleAddQuestion() {
        QuestionDTO question = availableQuestionsTable.getSelectionModel().getSelectedItem();
        if (question == null) {
            setStatus("Select an available question to add.");
            return;
        }
        if (!isEditable()) {
            setStatus("This exam is read-only.");
            return;
        }
        if (containsQuestion(question.getQuestionId())) {
            setStatus("Question #" + question.getQuestionId() + " is already selected.");
            return;
        }
        selectedQuestionItems.add(SelectedQuestionItem.fromQuestion(question));
        renumberSelectedQuestions();
        selectedQuestionsTable.getSelectionModel().selectLast();
        updateTotal();
        setStatus("Question added. Edit its score in the selected-question table.");
    }

    @FXML
    private void handleRemoveQuestion() {
        int index = selectedQuestionsTable.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            setStatus("Select a question to remove.");
            return;
        }
        if (!isEditable()) {
            setStatus("This exam is read-only.");
            return;
        }
        selectedQuestionItems.remove(index);
        renumberSelectedQuestions();
        if (!selectedQuestionItems.isEmpty()) {
            selectedQuestionsTable.getSelectionModel().select(
                    Math.min(index, selectedQuestionItems.size() - 1)
            );
        }
        updateTotal();
    }

    @FXML
    private void handleMoveUp() {
        moveSelectedQuestion(-1);
    }

    @FXML
    private void handleMoveDown() {
        moveSelectedQuestion(1);
    }

    private void moveSelectedQuestion(int offset) {
        int index = selectedQuestionsTable.getSelectionModel().getSelectedIndex();
        int destination = index + offset;
        if (!isEditable() || index < 0 || destination < 0
                || destination >= selectedQuestionItems.size()) {
            return;
        }
        SelectedQuestionItem item = selectedQuestionItems.remove(index);
        selectedQuestionItems.add(destination, item);
        renumberSelectedQuestions();
        selectedQuestionsTable.getSelectionModel().select(destination);
    }

    @FXML
    private void handleSaveDraft() {
        if (savePending || isAutomaticMode() || !isEditable()) {
            return;
        }
        String validation = validateEditor();
        if (validation != null) {
            setStatus(validation);
            return;
        }

        List<ExamQuestionSelectionPayload> selections =
                buildSelectionPayloads(selectedQuestionItems);
        savePending = true;
        updateActionState();
        setStatus(loadedExam == null ? "Creating DRAFT exam..." : "Saving new DRAFT version...");

        if (loadedExam == null) {
            CourseSummaryDTO course = courseComboBox.getValue();
            CreateExamPayload payload = new CreateExamPayload(
                    course.getCourseId(),
                    titleField.getText(),
                    Integer.parseInt(durationField.getText().trim()),
                    teacherNotesArea.getText(),
                    studentInstructionsArea.getText(),
                    selections
            );
            examClientController.createExam(payload).whenComplete((created, error) ->
                    finishSave(created, error, "DRAFT exam created successfully."));
        } else {
            int examId = loadedExam.getExamId();
            UpdateExamPayload payload = new UpdateExamPayload(
                    examId,
                    loadedExam.getVersionNo(),
                    titleField.getText(),
                    Integer.parseInt(durationField.getText().trim()),
                    teacherNotesArea.getText(),
                    studentInstructionsArea.getText(),
                    selections
            );
            examClientController.updateExam(payload).whenComplete((updated, error) -> {
                if (error != null && "Exam version conflict".equals(cleanError(error))) {
                    Platform.runLater(() -> {
                        if (closed) {
                            return;
                        }
                        savePending = false;
                        openExam(examId, "Exam version conflict");
                    });
                } else {
                    finishSave(updated, error, "New DRAFT version saved successfully.");
                }
            });
        }
    }

    private void finishSave(ExamDTO saved, Throwable error, String successMessage) {
        Platform.runLater(() -> {
            if (closed) {
                return;
            }
            savePending = false;
            if (error != null) {
                setStatus(cleanError(error));
                updateActionState();
                return;
            }
            showExam(saved);
            loadExamSummaries(saved.getExamId(), successMessage);
            updateActionState();
        });
    }

    @FXML
    private void handleManualMode() {
        updateModeVisibility();
        if (courseComboBox.getValue() != null && isConfigured()) {
            loadActiveQuestions(courseComboBox.getValue().getCourseId());
        }
        setStatus("Manual mode: select questions and assign scores totaling 100.");
        updateActionState();
    }

    @FXML
    private void handleAutomaticMode() {
        if (loadedExam != null) {
            selectManualMode();
            setStatus("Automatic mode is available only for a New Exam.");
            updateActionState();
            return;
        }
        questionRequestGeneration++;
        questionsLoading = false;
        availableQuestions.clear();
        updateModeVisibility();
        setStatus("Automatic mode: matching ACTIVE questions are selected by the server.");
        updateActionState();
    }

    @FXML
    private void handleGenerateDraft() {
        if (generationPending || loadedExam != null || !isAutomaticMode()) {
            return;
        }

        String validation = validateAutomaticInput(
                courseComboBox.getValue(),
                titleField.getText(),
                durationField.getText(),
                studentInstructionsArea.getText(),
                topicField.getText(),
                difficultyComboBox.getValue(),
                questionCountField.getText()
        );
        if (validation != null) {
            setStatus(validation);
            return;
        }

        GenerateExamPayload payload = buildAutomaticPayload(
                courseComboBox.getValue(),
                titleField.getText(),
                durationField.getText(),
                teacherNotesArea.getText(),
                studentInstructionsArea.getText(),
                topicField.getText(),
                difficultyComboBox.getValue(),
                questionCountField.getText()
        );
        long generation = ++automaticRequestGeneration;
        generationPending = true;
        updateActionState();
        setStatus("Generating DRAFT exam...");

        examClientController.generateExam(payload).whenComplete((generated, error) ->
                Platform.runLater(() -> {
                    if (closed || generation != automaticRequestGeneration) {
                        return;
                    }
                    generationPending = false;
                    if (error != null) {
                        setStatus(cleanError(error));
                        updateActionState();
                        return;
                    }
                    showExam(generated);
                    loadExamSummaries(
                            generated.getExamId(),
                            "Automatic DRAFT exam generated successfully."
                    );
                    updateActionState();
                })
        );
    }

    @FXML
    private void handleSubmitForApproval() {
        if (loadedExam == null || loadedExam.getStatus() != ExamStatus.DRAFT
                || submitPending) {
            setStatus("Save a DRAFT exam before submitting it for approval.");
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Submit exam for approval");
        confirmation.setHeaderText("Submit " + safe(loadedExam.getExamCode()) + "?");
        confirmation.setContentText(
                "The exam will become read-only until a coordinator reviews it."
        );
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }

        submitPending = true;
        updateActionState();
        ExamVersionPayload payload = new ExamVersionPayload(
                loadedExam.getExamId(),
                loadedExam.getVersionNo()
        );
        examClientController.submitExamForApproval(payload).whenComplete((submitted, error) ->
                Platform.runLater(() -> {
                    if (closed) {
                        return;
                    }
                    submitPending = false;
                    if (error != null) {
                        setStatus(cleanError(error));
                        updateActionState();
                        return;
                    }
                    showExam(submitted);
                    loadExamSummaries(
                            submitted.getExamId(),
                            "Exam submitted for coordinator approval."
                    );
                    updateActionState();
                })
        );
    }

    @FXML
    private void handleRefresh() {
        loadAssignedCourses();
        loadExamSummaries(loadedExam == null ? null : loadedExam.getExamId(), null);
        if (courseComboBox.getValue() != null && !isAutomaticMode()) {
            loadActiveQuestions(courseComboBox.getValue().getCourseId());
        }
    }

    @FXML
    private void handleOpenQuestionBank() {
        if (questionBankHandler == null || savePending || submitPending
                || generationPending) {
            setStatus("Question Bank is unavailable.");
            return;
        }
        try {
            questionBankHandler.run();
            closeForNavigation();
        } catch (RuntimeException exception) {
            setStatus("Unable to open Question Bank.");
        }
    }

    @FXML
    private void handleBack() {
        if (backHandler == null) {
            setStatus("Back navigation is unavailable.");
            return;
        }
        if (!confirmDiscardIfDirty()) return;
        closeForNavigation();
        try {
            backHandler.run();
            illustrationRenderer.dispose();
        } catch (RuntimeException exception) {
            closed = false;
            setStatus("Unable to return to dashboard.");
        }
    }

    public EditorState snapshotEditorState() {
        return new EditorState(
                loadedExam,
                courseComboBox.getValue() == null
                        ? 0 : courseComboBox.getValue().getCourseId(),
                titleField.getText(), durationField.getText(),
                teacherNotesArea.getText(), studentInstructionsArea.getText(),
                isAutomaticMode(), topicField.getText(),
                difficultyComboBox.getValue(), questionCountField.getText(),
                selectedQuestionItems.stream().map(SelectedQuestionItem::copy).toList(),
                baselineFingerprint,
                isDirty()
        );
    }

    private void restoreEditorState(EditorState state) {
        loadedExam = state.loadedExam;
        CourseSummaryDTO course = findCourse(state.courseId);
        if (state.courseId > 0 && course == null) {
            setStatus("The preserved course is no longer available.");
        }
        courseComboBox.setValue(course);
        courseComboBox.setDisable(loadedExam != null);
        titleField.setText(state.title);
        durationField.setText(state.duration);
        teacherNotesArea.setText(state.teacherNotes);
        studentInstructionsArea.setText(state.studentInstructions);
        topicField.setText(state.topic);
        difficultyComboBox.setValue(state.difficulty);
        questionCountField.setText(state.questionCount);
        if (state.automatic && loadedExam == null) automaticModeRadio.setSelected(true);
        else manualModeRadio.setSelected(true);
        updateModeVisibility();
        selectedQuestionItems.setAll(
                state.selectedQuestions.stream().map(SelectedQuestionItem::copy).toList()
        );
        renumberSelectedQuestions();
        if (loadedExam == null) {
            loadedExamLabel.setText("New DRAFT exam");
            versionStatusLabel.setText("Unsaved DRAFT");
        } else {
            loadedExamLabel.setText(safe(loadedExam.getExamCode()) + " — "
                    + safe(loadedExam.getTitle()));
            versionStatusLabel.setText("Version " + loadedExam.getVersionNo()
                    + " • " + loadedExam.getStatus());
            updateEditabilityMessage();
        }
        showRejectionReason(loadedExam);
        baselineFingerprint = state.baselineFingerprint;
        updateTotal();
        if (course != null && !isAutomaticMode()) {
            loadActiveQuestions(course.getCourseId());
        }
        setStatus(state.dirty
                ? "Unfinished Exam Builder state restored."
                : "Exam Builder state restored.");
    }

    private void closeForNavigation() {
        closed = true;
        courseRequestGeneration++;
        summaryRequestGeneration++;
        detailRequestGeneration++;
        questionRequestGeneration++;
        automaticRequestGeneration++;
        if (illustrationRenderer != null) illustrationRenderer.dispose();
    }

    private boolean confirmDiscardIfDirty() {
        if (!isDirty()) return true;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard unsaved exam changes?");
        confirmation.setHeaderText("Unsaved Exam Builder changes will be lost.");
        confirmation.setContentText("Choose Cancel to keep editing.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private boolean isDirty() {
        return baselineFingerprint != null
                && !baselineFingerprint.equals(editorFingerprint());
    }

    private String editorFingerprint() {
        StringBuilder value = new StringBuilder();
        value.append(loadedExam == null ? 0 : loadedExam.getExamId()).append('|')
                .append(loadedExam == null ? 0 : loadedExam.getVersionNo()).append('|')
                .append(courseComboBox.getValue() == null ? 0
                        : courseComboBox.getValue().getCourseId()).append('|')
                .append(titleField.getText()).append('|').append(durationField.getText())
                .append('|').append(teacherNotesArea.getText()).append('|')
                .append(studentInstructionsArea.getText()).append('|')
                .append(isAutomaticMode()).append('|').append(topicField.getText())
                .append('|').append(difficultyComboBox.getValue()).append('|')
                .append(questionCountField.getText());
        for (SelectedQuestionItem item : selectedQuestionItems) {
            value.append('|').append(item.questionId).append(':')
                    .append(item.questionVersionNo).append(':')
                    .append(item.orderNumber).append(':').append(item.score);
        }
        return value.toString();
    }

    private String validateEditor() {
        if (courseComboBox.getValue() == null) {
            return "Select an assigned course.";
        }
        if (isBlank(titleField.getText())) {
            return "Exam title is required.";
        }
        final int duration;
        try {
            duration = Integer.parseInt(durationField.getText().trim());
        } catch (RuntimeException exception) {
            return "Duration must be a positive whole number.";
        }
        if (duration <= 0) {
            return "Duration must be a positive whole number.";
        }
        if (isBlank(studentInstructionsArea.getText())) {
            return "Student instructions are required.";
        }
        if (selectedQuestionItems.isEmpty()) {
            return "Select at least one question.";
        }
        for (SelectedQuestionItem item : selectedQuestionItems) {
            if (!Double.isFinite(item.getScore()) || item.getScore() <= 0) {
                return "Question scores must be positive and finite.";
            }
        }
        if (calculateTotal(selectedQuestionItems).compareTo(new BigDecimal("100.00")) != 0) {
            return "Exam total score must equal 100.";
        }
        return null;
    }

    private void renumberSelectedQuestions() {
        for (int index = 0; index < selectedQuestionItems.size(); index++) {
            selectedQuestionItems.get(index).setOrderNumber(index + 1);
        }
        selectedQuestionsTable.refresh();
        updateActionState();
    }

    private void updateTotal() {
        BigDecimal total = calculateTotal(selectedQuestionItems);
        totalScoreLabel.setText(total.stripTrailingZeros().toPlainString() + " / 100");
        boolean exact = total.compareTo(new BigDecimal("100.00")) == 0;
        totalFeedbackLabel.setText(exact ? "Ready to save" : "Total must equal exactly 100");
        totalFeedbackLabel.setStyle(exact
                ? "-fx-text-fill: #15803d; -fx-font-weight: bold;"
                : "-fx-text-fill: #b45309; -fx-font-weight: bold;");
        updateActionState();
    }

    static BigDecimal calculateTotal(List<SelectedQuestionItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (SelectedQuestionItem item : items) {
            if (Double.isFinite(item.getScore())) {
                total = total.add(BigDecimal.valueOf(item.getScore()));
            }
        }
        return total;
    }

    static List<ExamQuestionSelectionPayload> buildSelectionPayloads(
            List<SelectedQuestionItem> items) {
        List<ExamQuestionSelectionPayload> payloads = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            SelectedQuestionItem item = items.get(index);
            payloads.add(new ExamQuestionSelectionPayload(
                    item.getQuestionId(),
                    item.getQuestionVersionNo(),
                    index + 1,
                    item.getScore()
            ));
        }
        return List.copyOf(payloads);
    }

    static List<DifficultyLevel> automaticDifficulties() {
        return List.of(DifficultyLevel.values());
    }

    static String validateAutomaticInput(CourseSummaryDTO course, String title,
                                         String durationText, String studentInstructions,
                                         String topic, DifficultyLevel difficulty,
                                         String questionCountText) {
        if (course == null) {
            return "Select an assigned course.";
        }
        if (isBlankText(title)) {
            return "Exam title is required.";
        }
        if (!isPositiveInteger(durationText)) {
            return "Duration must be a positive whole number.";
        }
        if (isBlankText(studentInstructions)) {
            return "Student instructions are required.";
        }
        if (isBlankText(topic)) {
            return "Topic is required.";
        }
        if (difficulty == null) {
            return "Difficulty is required.";
        }
        if (!isPositiveInteger(questionCountText)) {
            return "Question count must be a positive whole number.";
        }
        return null;
    }

    static GenerateExamPayload buildAutomaticPayload(
            CourseSummaryDTO course, String title, String durationText,
            String teacherNotes, String studentInstructions, String topic,
            DifficultyLevel difficulty, String questionCountText) {
        return new GenerateExamPayload(
                course.getCourseId(),
                title.trim(),
                Integer.parseInt(durationText.trim()),
                isBlankText(teacherNotes) ? "" : teacherNotes.trim(),
                studentInstructions.trim(),
                topic.trim(),
                difficulty,
                Integer.parseInt(questionCountText.trim())
        );
    }

    static List<SelectedQuestionItem> selectedItemsFromExam(
            List<ExamQuestionDTO> questions) {
        List<ExamQuestionDTO> ordered = new ArrayList<>(questions);
        ordered.sort(Comparator.comparingInt(ExamQuestionDTO::getOrderNumber));
        return ordered.stream().map(SelectedQuestionItem::fromSnapshot).toList();
    }

    static boolean isReadOnly(ExamStatus status) {
        return status == ExamStatus.PENDING_APPROVAL;
    }

    private boolean isEditable() {
        return loadedExam == null || !isReadOnly(loadedExam.getStatus());
    }

    private boolean isAutomaticMode() {
        return automaticModeRadio != null && automaticModeRadio.isSelected();
    }

    private void selectManualMode() {
        if (manualModeRadio != null) {
            manualModeRadio.setSelected(true);
        }
        updateModeVisibility();
    }

    private void updateModeVisibility() {
        boolean automatic = isAutomaticMode() && loadedExam == null;
        if (automaticCriteriaPane != null) {
            automaticCriteriaPane.setManaged(automatic);
            automaticCriteriaPane.setVisible(automatic);
        }
        if (manualQuestionPane != null) {
            manualQuestionPane.setManaged(!automatic);
            manualQuestionPane.setVisible(!automatic);
        }
    }

    private boolean containsQuestion(int questionId) {
        return selectedQuestionItems.stream()
                .anyMatch(item -> item.getQuestionId() == questionId);
    }

    private void updateActionState() {
        boolean configured = isConfigured();
        boolean busy = savePending || submitPending || generationPending;
        boolean formEditable = configured && isEditable() && !busy;
        boolean automatic = isAutomaticMode() && loadedExam == null;
        boolean editable = formEditable && !automatic;
        boolean selectedAvailable = availableQuestionsTable != null
                && availableQuestionsTable.getSelectionModel().getSelectedItem() != null;
        int selectedIndex = selectedQuestionsTable == null
                ? -1 : selectedQuestionsTable.getSelectionModel().getSelectedIndex();

        if (courseComboBox != null) {
            courseComboBox.setDisable(!formEditable || loadedExam != null || coursesLoading);
        }
        if (titleField != null) titleField.setDisable(!formEditable);
        if (durationField != null) durationField.setDisable(!formEditable);
        if (teacherNotesArea != null) teacherNotesArea.setDisable(!formEditable);
        if (studentInstructionsArea != null) studentInstructionsArea.setDisable(!formEditable);
        if (manualModeRadio != null) manualModeRadio.setDisable(!configured || busy);
        if (automaticModeRadio != null) {
            automaticModeRadio.setDisable(!configured || busy || loadedExam != null);
        }
        if (topicField != null) topicField.setDisable(!formEditable || !automatic);
        if (difficultyComboBox != null) {
            difficultyComboBox.setDisable(!formEditable || !automatic);
        }
        if (questionCountField != null) {
            questionCountField.setDisable(!formEditable || !automatic);
        }
        if (generateButton != null) {
            generateButton.setDisable(!configured || !automatic || busy);
        }
        if (selectedQuestionsTable != null) selectedQuestionsTable.setEditable(editable);
        if (newButton != null) newButton.setDisable(!configured || busy);
        if (openButton != null) openButton.setDisable(!configured || busy || examLoading
                || examTable.getSelectionModel().getSelectedItem() == null);
        if (saveButton != null) saveButton.setDisable(!editable);
        if (submitButton != null) submitButton.setDisable(!configured || submitPending
                || loadedExam == null || loadedExam.getStatus() != ExamStatus.DRAFT);
        if (refreshButton != null) refreshButton.setDisable(!configured
                || coursesLoading || examsLoading || generationPending);
        if (backButton != null) backButton.setDisable(generationPending);
        if (questionBankButton != null) {
            questionBankButton.setDisable(!configured || busy || questionBankHandler == null);
        }
        if (addButton != null) addButton.setDisable(!editable || questionsLoading
                || !selectedAvailable);
        if (removeButton != null) removeButton.setDisable(!editable || selectedIndex < 0);
        if (moveUpButton != null) moveUpButton.setDisable(!editable || selectedIndex <= 0);
        if (moveDownButton != null) moveDownButton.setDisable(!editable || selectedIndex < 0
                || selectedIndex >= selectedQuestionItems.size() - 1);
    }

    private boolean isConfigured() {
        return !closed && client != null && examClientController != null
                && questionClientController != null;
    }

    private CourseSummaryDTO findCourse(int courseId) {
        return assignedCourses.stream()
                .filter(course -> course.getCourseId() == courseId)
                .findFirst()
                .orElse(null);
    }

    private void selectExamSummary(Integer examId) {
        examTable.getSelectionModel().clearSelection();
        if (examId == null) {
            return;
        }
        for (int index = 0; index < examSummaries.size(); index++) {
            if (examSummaries.get(index).getExamId() == examId) {
                examTable.getSelectionModel().select(index);
                examTable.scrollTo(index);
                return;
            }
        }
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

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private boolean isBlank(String value) {
        return isBlankText(value);
    }

    private static boolean isBlankText(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isPositiveInteger(String value) {
        if (isBlankText(value)) {
            return false;
        }
        try {
            return Integer.parseInt(value.trim()) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    static final class SelectedQuestionItem {
        private final int questionId;
        private final int questionVersionNo;
        private final String content;
        private final String topic;
        private final String difficulty;
        private final QuestionIllustrationDTO illustration;
        private final String answerOption1;
        private final String answerOption2;
        private final String answerOption3;
        private final String answerOption4;
        private final int correctOptionNumber;
        private int orderNumber;
        private double score;

        private SelectedQuestionItem(int questionId, int questionVersionNo,
                                     String content, String topic, String difficulty,
                                     QuestionIllustrationDTO illustration,
                                     String answerOption1,
                                     String answerOption2, String answerOption3,
                                     String answerOption4, int correctOptionNumber,
                                     int orderNumber, double score) {
            this.questionId = questionId;
            this.questionVersionNo = questionVersionNo;
            this.content = content;
            this.topic = topic;
            this.difficulty = difficulty;
            this.illustration = illustration;
            this.answerOption1 = answerOption1;
            this.answerOption2 = answerOption2;
            this.answerOption3 = answerOption3;
            this.answerOption4 = answerOption4;
            this.correctOptionNumber = correctOptionNumber;
            this.orderNumber = orderNumber;
            this.score = score;
        }

        static SelectedQuestionItem fromQuestion(QuestionDTO question) {
            return new SelectedQuestionItem(
                    question.getQuestionId(), question.getVersionNo(), question.getContent(),
                    question.getTopic(), question.getDifficulty(), question.getIllustration(),
                    question.getAnswerOption1(), question.getAnswerOption2(),
                    question.getAnswerOption3(), question.getAnswerOption4(),
                    question.getCorrectOptionNumber(), 0, 0
            );
        }

        static SelectedQuestionItem fromSnapshot(ExamQuestionDTO question) {
            return new SelectedQuestionItem(
                    question.getQuestionId(), question.getQuestionVersionNo(),
                    question.getContent(), question.getTopic(), question.getDifficulty(),
                    question.getIllustration(),
                    question.getAnswerOption1(),
                    question.getAnswerOption2(), question.getAnswerOption3(),
                    question.getAnswerOption4(), question.getCorrectOptionNumber(),
                    question.getOrderNumber(), question.getScore()
            );
        }

        int getQuestionId() { return questionId; }
        int getQuestionVersionNo() { return questionVersionNo; }
        String getContent() { return content; }
        String getTopic() { return topic; }
        String getDifficulty() { return difficulty; }
        QuestionIllustrationDTO getIllustration() { return illustration; }
        String getAnswerOption1() { return answerOption1; }
        String getAnswerOption2() { return answerOption2; }
        String getAnswerOption3() { return answerOption3; }
        String getAnswerOption4() { return answerOption4; }
        int getCorrectOptionNumber() { return correctOptionNumber; }
        int getOrderNumber() { return orderNumber; }
        double getScore() { return score; }
        void setOrderNumber(int orderNumber) { this.orderNumber = orderNumber; }
        void setScore(double score) { this.score = score; }

        SelectedQuestionItem copy() {
            return new SelectedQuestionItem(
                    questionId, questionVersionNo, content, topic, difficulty,
                    illustration, answerOption1, answerOption2, answerOption3,
                    answerOption4, correctOptionNumber, orderNumber, score
            );
        }
    }

    public static final class EditorState {
        private final ExamDTO loadedExam;
        private final int courseId;
        private final String title;
        private final String duration;
        private final String teacherNotes;
        private final String studentInstructions;
        private final boolean automatic;
        private final String topic;
        private final DifficultyLevel difficulty;
        private final String questionCount;
        private final List<SelectedQuestionItem> selectedQuestions;
        private final String baselineFingerprint;
        private final boolean dirty;

        private EditorState(ExamDTO loadedExam, int courseId, String title,
                            String duration, String teacherNotes,
                            String studentInstructions, boolean automatic,
                            String topic, DifficultyLevel difficulty,
                            String questionCount,
                            List<SelectedQuestionItem> selectedQuestions,
                            String baselineFingerprint, boolean dirty) {
            this.loadedExam = loadedExam;
            this.courseId = courseId;
            this.title = title;
            this.duration = duration;
            this.teacherNotes = teacherNotes;
            this.studentInstructions = studentInstructions;
            this.automatic = automatic;
            this.topic = topic;
            this.difficulty = difficulty;
            this.questionCount = questionCount;
            this.selectedQuestions = selectedQuestions.stream()
                    .map(SelectedQuestionItem::copy).toList();
            this.baselineFingerprint = baselineFingerprint;
            this.dirty = dirty;
        }

        public boolean isDirty() { return dirty; }
    }

    public void chooseManualExam(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void selectQuestion(int questionId, double score) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void removeSelectedQuestion(int questionId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void saveExam(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void buildAutomaticExam(int courseId, String topic, DifficultyLevel difficulty,
                                   int numberOfQuestions, double totalScore) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void createExam(int courseId, int duration) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void manageQuestionBank() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void addNotes(String teacherNotes, String studentNotes) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void changeQuestionScore(int questionId, double score) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void chooseAutomaticExam(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
