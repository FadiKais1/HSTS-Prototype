package hsts.client.boundary;

import hsts.client.control.QuestionClientController;
import hsts.client.net.Client;
import hsts.common.CourseSummaryDTO;
import hsts.common.CreateQuestionPayload;
import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.QuestionVersionDTO;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class QuestionBankPageController {
    private static final String ALL = "All";
    private static final DateTimeFormatter HISTORY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObservableList<QuestionDTO> questions = FXCollections.observableArrayList();
    private final ObservableList<CourseSummaryDTO> assignedCourses =
            FXCollections.observableArrayList();

    private QuestionClientController questionClientController;
    private Client client;
    private Runnable backHandler;
    private long questionRequestGeneration;
    private boolean closed;
    private boolean loadingQuestions;
    private boolean createPending;
    private boolean updatePending;
    private boolean statusPending;
    private boolean historyPending;

    @FXML private ComboBox<CourseSummaryDTO> courseFilterComboBox;
    @FXML private ComboBox<CourseSummaryDTO> subjectFilterComboBox;
    @FXML private CheckBox allCoursesForSubjectCheckBox;
    @FXML private TextField topicFilterField;
    @FXML private ComboBox<String> difficultyFilterComboBox;
    @FXML private ComboBox<String> statusFilterComboBox;
    @FXML private Button applyFiltersButton;
    @FXML private Button clearFiltersButton;

    @FXML private TableView<QuestionDTO> tableView;
    @FXML private TableColumn<QuestionDTO, Number> idColumn;
    @FXML private TableColumn<QuestionDTO, String> contentColumn;
    @FXML private TableColumn<QuestionDTO, String> courseColumn;
    @FXML private TableColumn<QuestionDTO, String> subjectColumn;
    @FXML private TableColumn<QuestionDTO, String> topicColumn;
    @FXML private TableColumn<QuestionDTO, String> difficultyColumn;
    @FXML private TableColumn<QuestionDTO, String> statusColumn;
    @FXML private TableColumn<QuestionDTO, Number> versionColumn;

    @FXML private TextArea contentArea;
    @FXML private TextField topicField;
    @FXML private TextField illustrationPathField;
    @FXML private TextField option1Field;
    @FXML private TextField option2Field;
    @FXML private TextField option3Field;
    @FXML private TextField option4Field;
    @FXML private ComboBox<String> difficultyComboBox;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private RadioButton option1Radio;
    @FXML private RadioButton option2Radio;
    @FXML private RadioButton option3Radio;
    @FXML private RadioButton option4Radio;

    @FXML private Button createButton;
    @FXML private Button updateButton;
    @FXML private Button statusActionButton;
    @FXML private Button historyButton;
    @FXML private Button clearSelectionButton;

    @FXML private Label statusLabel;
    @FXML private Label serverStatusValue;
    @FXML private Label questionsCountValue;
    @FXML private Label currentActionValue;
    @FXML private Label selectedIdValue;
    @FXML private Label selectedCourseValue;
    @FXML private Label selectedSubjectValue;
    @FXML private Label selectedVersionValue;
    @FXML private Label selectedTypeValue;

    private ToggleGroup correctAnswerGroup;

    @FXML
    private void initialize() {
        setupFilterControls();
        setupEditorControls();
        setupTable();
        setConfiguredState(false);
        setStatus("Waiting for authenticated client.");
        setCurrentAction("Waiting for configuration");
    }

    public void configure(Client client, Runnable backHandler) {
        this.client = Objects.requireNonNull(client, "client");
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        this.questionClientController = new QuestionClientController(client);
        this.closed = false;

        serverStatusValue.setText("Connected to " + client.getHost() + ":" + client.getPort());
        setStatus("Loading assigned courses...");
        setCurrentAction("Loading courses");
        loadAssignedCourses();
    }

    private void setupFilterControls() {
        configureCourseDisplay(courseFilterComboBox, false);
        configureCourseDisplay(subjectFilterComboBox, true);

        difficultyFilterComboBox.setItems(FXCollections.observableArrayList(
                ALL, DifficultyLevel.EASY.name(), DifficultyLevel.MEDIUM.name(),
                DifficultyLevel.HARD.name()
        ));
        difficultyFilterComboBox.setValue(ALL);

        statusFilterComboBox.setItems(FXCollections.observableArrayList(
                ALL, QuestionStatus.ACTIVE.name(), QuestionStatus.INACTIVE.name()
        ));
        statusFilterComboBox.setValue(ALL);

        courseFilterComboBox.valueProperty().addListener((observable, oldCourse, course) -> {
            if (course != null) {
                selectSubject(course.getSubjectId());
                allCoursesForSubjectCheckBox.setSelected(false);
            }
        });
        subjectFilterComboBox.valueProperty().addListener((observable, oldSubject, subject) -> {
            CourseSummaryDTO course = courseFilterComboBox.getValue();
            if (subject != null && course != null
                    && course.getSubjectId() != subject.getSubjectId()) {
                allCoursesForSubjectCheckBox.setSelected(true);
            }
        });
    }

    private void setupEditorControls() {
        difficultyComboBox.setItems(FXCollections.observableArrayList(
                DifficultyLevel.EASY.name(), DifficultyLevel.MEDIUM.name(),
                DifficultyLevel.HARD.name()
        ));
        difficultyComboBox.setValue(DifficultyLevel.EASY.name());

        statusComboBox.setItems(FXCollections.observableArrayList(
                QuestionStatus.ACTIVE.name(), QuestionStatus.INACTIVE.name()
        ));
        statusComboBox.setValue(QuestionStatus.ACTIVE.name());

        correctAnswerGroup = new ToggleGroup();
        configureCorrectAnswer(option1Radio, 1);
        configureCorrectAnswer(option2Radio, 2);
        configureCorrectAnswer(option3Radio, 3);
        configureCorrectAnswer(option4Radio, 4);
    }

    private void configureCorrectAnswer(RadioButton radioButton, int optionNumber) {
        radioButton.setToggleGroup(correctAnswerGroup);
        radioButton.setUserData(optionNumber);
    }

    private void setupTable() {
        tableView.setItems(questions);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        idColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuestionId()));
        contentColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getContent())));
        courseColumn.setCellValueFactory(data ->
                new SimpleStringProperty(courseName(data.getValue().getCourseId())));
        subjectColumn.setCellValueFactory(data ->
                new SimpleStringProperty(subjectName(data.getValue().getSubjectId())));
        topicColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getTopic())));
        difficultyColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getDifficulty())));
        statusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getStatus())));
        versionColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getVersionNo()));

        tableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldQuestion, selectedQuestion) -> {
                    if (selectedQuestion == null) {
                        clearEditor();
                    } else {
                        showSelectedQuestion(selectedQuestion);
                        setStatus("Question #" + selectedQuestion.getQuestionId() + " selected.");
                    }
                    updateActionState();
                }
        );
    }

    private void configureCourseDisplay(ComboBox<CourseSummaryDTO> comboBox,
                                        boolean subjectOnly) {
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseSummaryDTO course) {
                if (course == null) {
                    return "";
                }
                return subjectOnly
                        ? course.getSubjectName()
                        : course.getCourseName() + " — " + course.getSubjectName();
            }

            @Override
            public CourseSummaryDTO fromString(String value) {
                return null;
            }
        });
    }

    private void loadAssignedCourses() {
        setConfiguredState(false);
        questionClientController.getMyCourses().whenComplete((courses, error) ->
                Platform.runLater(() -> {
                    if (closed) {
                        return;
                    }
                    if (error != null) {
                        assignedCourses.clear();
                        questions.clear();
                        questionsCountValue.setText("0");
                        setStatus(getCleanError(error));
                        setCurrentAction("Course load failed");
                        setConfiguredState(false);
                        return;
                    }

                    assignedCourses.setAll(courses);
                    courseFilterComboBox.setItems(assignedCourses);
                    subjectFilterComboBox.setItems(uniqueSubjects(courses));
                    setConfiguredState(!courses.isEmpty());

                    if (courses.isEmpty()) {
                        questions.clear();
                        questionsCountValue.setText("0");
                        setStatus("No assigned courses are available for question management.");
                        setCurrentAction("No assigned courses");
                        return;
                    }

                    CourseSummaryDTO firstCourse = courses.get(0);
                    courseFilterComboBox.setValue(firstCourse);
                    selectSubject(firstCourse.getSubjectId());
                    loadQuestions(null, "Questions loaded successfully.");
                })
        );
    }

    private ObservableList<CourseSummaryDTO> uniqueSubjects(List<CourseSummaryDTO> courses) {
        Map<Integer, CourseSummaryDTO> subjects = new LinkedHashMap<>();
        for (CourseSummaryDTO course : courses) {
            subjects.putIfAbsent(course.getSubjectId(), course);
        }
        return FXCollections.observableArrayList(subjects.values());
    }

    @FXML
    private void handleApplyFilters() {
        loadQuestions(null, "Questions refreshed successfully.");
    }

    @FXML
    private void handleClearFilters() {
        if (assignedCourses.isEmpty()) {
            setStatus("No assigned courses are available for filtering.");
            return;
        }
        CourseSummaryDTO firstCourse = assignedCourses.get(0);
        courseFilterComboBox.setValue(firstCourse);
        selectSubject(firstCourse.getSubjectId());
        allCoursesForSubjectCheckBox.setSelected(false);
        topicFilterField.clear();
        difficultyFilterComboBox.setValue(ALL);
        statusFilterComboBox.setValue(ALL);
        loadQuestions(null, "Filters cleared.");
    }

    private void loadQuestions(Integer questionIdToSelect, String successMessage) {
        if (!isConfigured()) {
            setStatus("Question Bank is not configured.");
            return;
        }

        QuestionFilterPayload filter = buildFilterPayload();
        long requestGeneration = ++questionRequestGeneration;
        loadingQuestions = true;
        updateActionState();
        setStatus("Loading questions...");
        setCurrentAction("Loading questions");

        questionClientController.listQuestions(filter).whenComplete((loadedQuestions, error) ->
                Platform.runLater(() -> {
                    if (closed || requestGeneration != questionRequestGeneration) {
                        return;
                    }
                    loadingQuestions = false;
                    if (error != null) {
                        setStatus(getCleanError(error));
                        setCurrentAction("Question load failed");
                        updateActionState();
                        return;
                    }

                    questions.setAll(loadedQuestions);
                    questionsCountValue.setText(String.valueOf(loadedQuestions.size()));
                    selectQuestion(questionIdToSelect);
                    setStatus(successMessage);
                    setCurrentAction("Questions loaded");
                    updateActionState();
                })
        );
    }

    private QuestionFilterPayload buildFilterPayload() {
        CourseSummaryDTO course = courseFilterComboBox.getValue();
        CourseSummaryDTO subject = subjectFilterComboBox.getValue();
        Integer courseId = allCoursesForSubjectCheckBox.isSelected() || course == null
                ? null : course.getCourseId();
        Integer subjectId = subject == null ? null : subject.getSubjectId();
        String topic = topicFilterField.getText() == null
                ? null : topicFilterField.getText().trim();
        if (topic != null && topic.isEmpty()) {
            topic = null;
        }
        DifficultyLevel difficulty = enumFilter(
                difficultyFilterComboBox.getValue(), DifficultyLevel.class
        );
        QuestionStatus status = enumFilter(
                statusFilterComboBox.getValue(), QuestionStatus.class
        );
        return new QuestionFilterPayload(courseId, subjectId, topic, difficulty, status);
    }

    private <E extends Enum<E>> E enumFilter(String value, Class<E> enumType) {
        return value == null || ALL.equals(value) ? null : Enum.valueOf(enumType, value);
    }

    @FXML
    private void handleCreateQuestion() {
        if (!isConfigured() || assignedCourses.isEmpty() || createPending) {
            setStatus("No assigned course is available for question creation.");
            return;
        }

        Optional<CreateQuestionPayload> result = showCreateDialog();
        if (result.isEmpty()) {
            return;
        }

        CreateQuestionPayload payload = result.get();
        createPending = true;
        updateActionState();
        setStatus("Creating question...");
        setCurrentAction("Creating question");

        questionClientController.createQuestion(payload).whenComplete((createdQuestion, error) ->
                Platform.runLater(() -> {
                    if (closed) {
                        return;
                    }
                    createPending = false;
                    if (error != null) {
                        setStatus(getCleanError(error));
                        setCurrentAction("Create failed");
                        updateActionState();
                        return;
                    }

                    selectCourse(payload.getCourseId());
                    allCoursesForSubjectCheckBox.setSelected(false);
                    topicFilterField.clear();
                    difficultyFilterComboBox.setValue(ALL);
                    statusFilterComboBox.setValue(ALL);
                    loadQuestions(
                            createdQuestion.getQuestionId(),
                            "Question #" + createdQuestion.getQuestionId() + " created successfully."
                    );
                })
        );
    }

    private Optional<CreateQuestionPayload> showCreateDialog() {
        Dialog<CreateQuestionPayload> dialog = new Dialog<>();
        dialog.setTitle("Create Question");
        dialog.setHeaderText("Create a multiple-choice question");

        ComboBox<CourseSummaryDTO> courseBox = new ComboBox<>(assignedCourses);
        configureCourseDisplay(courseBox, false);
        CourseSummaryDTO selectedFilterCourse = courseFilterComboBox.getValue();
        courseBox.setValue(selectedFilterCourse == null
                ? assignedCourses.get(0) : selectedFilterCourse);
        TextArea content = new TextArea();
        content.setPrefRowCount(3);
        TextField topic = new TextField();
        ComboBox<DifficultyLevel> difficulty = new ComboBox<>(
                FXCollections.observableArrayList(DifficultyLevel.values())
        );
        difficulty.setValue(DifficultyLevel.EASY);
        TextField illustration = new TextField();
        TextField option1 = new TextField();
        TextField option2 = new TextField();
        TextField option3 = new TextField();
        TextField option4 = new TextField();
        ComboBox<Integer> correctOption = new ComboBox<>(
                FXCollections.observableArrayList(1, 2, 3, 4)
        );
        correctOption.setValue(1);
        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #b91c1c;");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(10));
        addFormRow(form, 0, "Course *", courseBox);
        addFormRow(form, 1, "Content *", content);
        addFormRow(form, 2, "Topic *", topic);
        addFormRow(form, 3, "Difficulty *", difficulty);
        addFormRow(form, 4, "Illustration path", illustration);
        addFormRow(form, 5, "Option 1 *", option1);
        addFormRow(form, 6, "Option 2 *", option2);
        addFormRow(form, 7, "Option 3 *", option3);
        addFormRow(form, 8, "Option 4 *", option4);
        addFormRow(form, 9, "Correct option *", correctOption);
        form.add(validation, 0, 10, 2, 1);

        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new ScrollPane(form));
        Node createNode = dialog.getDialogPane().lookupButton(createType);
        createNode.addEventFilter(ActionEvent.ACTION, event -> {
            String message = validateQuestionForm(
                    courseBox.getValue(), content.getText(), topic.getText(), difficulty.getValue(),
                    option1.getText(), option2.getText(), option3.getText(), option4.getText(),
                    correctOption.getValue()
            );
            if (message != null) {
                validation.setText(message);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == createType
                ? new CreateQuestionPayload(
                        courseBox.getValue().getCourseId(),
                        content.getText(),
                        topic.getText(),
                        difficulty.getValue(),
                        illustration.getText(),
                        option1.getText(), option2.getText(), option3.getText(), option4.getText(),
                        correctOption.getValue()
                )
                : null);
        return dialog.showAndWait();
    }

    private void addFormRow(GridPane form, int row, String label, Node control) {
        form.add(new Label(label), 0, row);
        form.add(control, 1, row);
    }

    private String validateQuestionForm(CourseSummaryDTO course, String content, String topic,
                                        DifficultyLevel difficulty, String option1, String option2,
                                        String option3, String option4, Integer correctOption) {
        if (course == null) {
            return "Course is required.";
        }
        if (isBlank(content) || isBlank(topic)) {
            return "Content and topic are required.";
        }
        if (difficulty == null) {
            return "Difficulty is required.";
        }
        if (isBlank(option1) || isBlank(option2) || isBlank(option3) || isBlank(option4)) {
            return "All four answer options are required.";
        }
        if (correctOption == null || correctOption < 1 || correctOption > 4) {
            return "Correct answer number must be between 1 and 4.";
        }
        return null;
    }

    @FXML
    private void handleUpdateQuestion() {
        QuestionDTO selectedQuestion = requireSelectedQuestion();
        if (selectedQuestion == null || updatePending) {
            return;
        }
        String validation = validateQuestionForm(
                courseById(selectedQuestion.getCourseId()),
                contentArea.getText(), topicField.getText(),
                parseDifficulty(difficultyComboBox.getValue()),
                option1Field.getText(), option2Field.getText(), option3Field.getText(),
                option4Field.getText(), getSelectedCorrectOptionNumber()
        );
        if (validation != null) {
            setStatus(validation);
            return;
        }

        UpdateQuestionPayload payload = new UpdateQuestionPayload(
                selectedQuestion.getQuestionId(),
                contentArea.getText(),
                topicField.getText(),
                difficultyComboBox.getValue(),
                statusComboBox.getValue(),
                illustrationPathField.getText(),
                option1Field.getText(), option2Field.getText(), option3Field.getText(),
                option4Field.getText(), getSelectedCorrectOptionNumber(),
                selectedQuestion.getVersionNo()
        );

        updatePending = true;
        updateActionState();
        setStatus("Updating question...");
        setCurrentAction("Updating question");

        questionClientController.updateQuestion(payload).whenComplete((updatedQuestion, error) ->
                Platform.runLater(() -> {
                    if (closed) {
                        return;
                    }
                    updatePending = false;
                    if (error != null) {
                        String message = getCleanError(error);
                        if ("Question version conflict".equals(message)) {
                            loadQuestions(selectedQuestion.getQuestionId(), message);
                        } else {
                            setStatus(message);
                            setCurrentAction("Update failed");
                            updateActionState();
                        }
                        return;
                    }
                    loadQuestions(
                            updatedQuestion.getQuestionId(),
                            "Question updated to version " + updatedQuestion.getVersionNo() + "."
                    );
                })
        );
    }

    @FXML
    private void handleToggleStatus() {
        QuestionDTO selectedQuestion = requireSelectedQuestion();
        if (selectedQuestion == null || statusPending) {
            return;
        }
        boolean active = QuestionStatus.ACTIVE.name().equals(selectedQuestion.getStatus());
        String action = active ? "deactivate" : "activate";
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm status change");
        confirmation.setHeaderText("Confirm question " + action);
        confirmation.setContentText(
                "Do you want to " + action + " question #"
                        + selectedQuestion.getQuestionId() + "?"
        );
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }

        statusPending = true;
        updateActionState();
        setStatus("Changing question status...");
        CompletableFuture<QuestionDTO> operation = active
                ? questionClientController.deactivateQuestion(selectedQuestion.getQuestionId())
                : questionClientController.activateQuestion(selectedQuestion.getQuestionId());
        operation.whenComplete((updatedQuestion, error) -> Platform.runLater(() -> {
            if (closed) {
                return;
            }
            statusPending = false;
            if (error != null) {
                setStatus(getCleanError(error));
                setCurrentAction("Status change failed");
                updateActionState();
                return;
            }
            loadQuestions(
                    updatedQuestion.getQuestionId(),
                    "Question " + (active ? "deactivated" : "activated") + " successfully."
            );
        }));
    }

    @FXML
    private void handleShowHistory() {
        QuestionDTO selectedQuestion = requireSelectedQuestion();
        if (selectedQuestion == null || historyPending) {
            return;
        }
        historyPending = true;
        updateActionState();
        setStatus("Loading version history...");
        questionClientController.getQuestionHistory(selectedQuestion.getQuestionId())
                .whenComplete((history, error) -> Platform.runLater(() -> {
                    if (closed) {
                        return;
                    }
                    historyPending = false;
                    updateActionState();
                    if (error != null) {
                        setStatus(getCleanError(error));
                        return;
                    }
                    showHistoryDialog(selectedQuestion.getQuestionId(), history);
                    setStatus("Loaded " + history.size() + " immutable version(s).");
                }));
    }

    private void showHistoryDialog(int questionId, List<QuestionVersionDTO> history) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Question Version History");
        dialog.setHeaderText("Question #" + questionId + " — newest version first");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox versions = new VBox(10);
        versions.setPadding(new Insets(10));
        if (history.isEmpty()) {
            versions.getChildren().add(new Label("No versions are available."));
        } else {
            for (QuestionVersionDTO version : history) {
                TextArea details = new TextArea(formatVersion(version));
                details.setEditable(false);
                details.setWrapText(true);
                details.setPrefRowCount(13);
                versions.getChildren().add(details);
            }
        }
        ScrollPane scrollPane = new ScrollPane(versions);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportWidth(760);
        scrollPane.setPrefViewportHeight(560);
        dialog.getDialogPane().setContent(scrollPane);
        dialog.showAndWait();
    }

    private String formatVersion(QuestionVersionDTO version) {
        String createdAt = version.getCreatedAt() == null
                ? "" : HISTORY_TIME_FORMAT.format(version.getCreatedAt());
        return "Version: " + version.getVersionNo()
                + "\nQuestion ID: " + version.getQuestionId()
                + "\nCourse ID: " + version.getCourseId()
                + "\nType: " + version.getType()
                + "\nDifficulty: " + version.getDifficulty()
                + "\nTopic: " + safe(version.getTopic())
                + "\nContent: " + safe(version.getContent())
                + "\nIllustration: " + safe(version.getIllustrationPath())
                + "\nOption 1: " + safe(version.getAnswerOption1())
                + "\nOption 2: " + safe(version.getAnswerOption2())
                + "\nOption 3: " + safe(version.getAnswerOption3())
                + "\nOption 4: " + safe(version.getAnswerOption4())
                + "\nCorrect option: " + version.getCorrectOptionNumber()
                + "\nUpdated by user: " + version.getCreatedByUserId()
                + "\nCreated at: " + createdAt;
    }

    @FXML
    private void handleClearSelection() {
        tableView.getSelectionModel().clearSelection();
        setStatus("Selection cleared.");
    }

    @FXML
    private void handleBack() {
        if (backHandler == null) {
            setStatus("Back navigation is unavailable.");
            return;
        }
        closed = true;
        questionRequestGeneration++;
        try {
            backHandler.run();
        } catch (RuntimeException exception) {
            closed = false;
            setStatus("Unable to return to dashboard.");
        }
    }

    private QuestionDTO requireSelectedQuestion() {
        QuestionDTO selectedQuestion = tableView.getSelectionModel().getSelectedItem();
        if (selectedQuestion == null) {
            setStatus("Select a question first.");
        }
        return selectedQuestion;
    }

    private void showSelectedQuestion(QuestionDTO question) {
        selectedIdValue.setText(String.valueOf(question.getQuestionId()));
        selectedCourseValue.setText(courseName(question.getCourseId()));
        selectedSubjectValue.setText(subjectName(question.getSubjectId()));
        selectedVersionValue.setText(String.valueOf(question.getVersionNo()));
        selectedTypeValue.setText(safe(question.getType()));
        contentArea.setText(safe(question.getContent()));
        topicField.setText(safe(question.getTopic()));
        difficultyComboBox.setValue(defaultIfBlank(
                question.getDifficulty(), DifficultyLevel.EASY.name()
        ));
        statusComboBox.setValue(defaultIfBlank(
                question.getStatus(), QuestionStatus.ACTIVE.name()
        ));
        illustrationPathField.setText(safe(question.getIllustrationPath()));
        option1Field.setText(safe(question.getAnswerOption1()));
        option2Field.setText(safe(question.getAnswerOption2()));
        option3Field.setText(safe(question.getAnswerOption3()));
        option4Field.setText(safe(question.getAnswerOption4()));
        selectCorrectAnswer(question.getCorrectOptionNumber());
        updateStatusActionText(question);
    }

    private void clearEditor() {
        selectedIdValue.setText("-");
        selectedCourseValue.setText("-");
        selectedSubjectValue.setText("-");
        selectedVersionValue.setText("-");
        selectedTypeValue.setText("MULTIPLE_CHOICE");
        contentArea.clear();
        topicField.clear();
        difficultyComboBox.setValue(DifficultyLevel.EASY.name());
        statusComboBox.setValue(QuestionStatus.ACTIVE.name());
        illustrationPathField.clear();
        option1Field.clear();
        option2Field.clear();
        option3Field.clear();
        option4Field.clear();
        correctAnswerGroup.selectToggle(null);
        statusActionButton.setText("Activate / Deactivate");
    }

    private void updateStatusActionText(QuestionDTO question) {
        statusActionButton.setText(QuestionStatus.ACTIVE.name().equals(question.getStatus())
                ? "Deactivate" : "Activate");
    }

    private void updateActionState() {
        boolean configured = isConfigured() && !assignedCourses.isEmpty();
        boolean selected = tableView.getSelectionModel().getSelectedItem() != null;
        applyFiltersButton.setDisable(!configured || loadingQuestions);
        clearFiltersButton.setDisable(!configured || loadingQuestions);
        createButton.setDisable(!configured || createPending);
        updateButton.setDisable(!configured || !selected || updatePending || loadingQuestions);
        statusActionButton.setDisable(!configured || !selected || statusPending || loadingQuestions);
        historyButton.setDisable(!configured || !selected || historyPending);
        clearSelectionButton.setDisable(!selected);
    }

    private void setConfiguredState(boolean configured) {
        courseFilterComboBox.setDisable(!configured);
        subjectFilterComboBox.setDisable(!configured);
        allCoursesForSubjectCheckBox.setDisable(!configured);
        topicFilterField.setDisable(!configured);
        difficultyFilterComboBox.setDisable(!configured);
        statusFilterComboBox.setDisable(!configured);
        updateActionState();
    }

    private boolean isConfigured() {
        return !closed && client != null && client.isConnected()
                && questionClientController != null;
    }

    private void selectQuestion(Integer questionId) {
        tableView.getSelectionModel().clearSelection();
        if (questionId == null) {
            return;
        }
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).getQuestionId() == questionId) {
                tableView.getSelectionModel().select(index);
                tableView.scrollTo(index);
                return;
            }
        }
    }

    private void selectCourse(int courseId) {
        CourseSummaryDTO course = courseById(courseId);
        if (course != null) {
            courseFilterComboBox.setValue(course);
            selectSubject(course.getSubjectId());
        }
    }

    private void selectSubject(int subjectId) {
        for (CourseSummaryDTO subject : subjectFilterComboBox.getItems()) {
            if (subject.getSubjectId() == subjectId) {
                subjectFilterComboBox.setValue(subject);
                return;
            }
        }
    }

    private CourseSummaryDTO courseById(int courseId) {
        for (CourseSummaryDTO course : assignedCourses) {
            if (course.getCourseId() == courseId) {
                return course;
            }
        }
        return null;
    }

    private String courseName(int courseId) {
        CourseSummaryDTO course = courseById(courseId);
        return course == null ? "Course #" + courseId : course.getCourseName();
    }

    private String subjectName(int subjectId) {
        for (CourseSummaryDTO course : assignedCourses) {
            if (course.getSubjectId() == subjectId) {
                return course.getSubjectName();
            }
        }
        return "Subject #" + subjectId;
    }

    private DifficultyLevel parseDifficulty(String value) {
        return value == null ? null : DifficultyLevel.valueOf(value);
    }

    private int getSelectedCorrectOptionNumber() {
        if (correctAnswerGroup.getSelectedToggle() == null
                || !(correctAnswerGroup.getSelectedToggle().getUserData() instanceof Integer value)) {
            return 0;
        }
        return value;
    }

    private void selectCorrectAnswer(int optionNumber) {
        for (RadioButton radio : List.of(
                option1Radio, option2Radio, option3Radio, option4Radio
        )) {
            if (Objects.equals(radio.getUserData(), optionNumber)) {
                correctAnswerGroup.selectToggle(radio);
                return;
            }
        }
        correctAnswerGroup.selectToggle(null);
    }

    private String validateQuestionForm(CourseSummaryDTO course, String content, String topic,
                                        DifficultyLevel difficulty, String option1, String option2,
                                        String option3, String option4, int correctOption) {
        return validateQuestionForm(
                course, content, topic, difficulty, option1, option2, option3, option4,
                Integer.valueOf(correctOption)
        );
    }

    private String getCleanError(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? "Unable to complete the request." : current.getMessage();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void setCurrentAction(String message) {
        currentActionValue.setText(message);
    }

    public void close() {
        closed = true;
        questionRequestGeneration++;
        questionClientController = null;
        client = null;
        backHandler = null;
    }
}
