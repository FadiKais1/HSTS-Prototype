package hsts.client.boundary;

import hsts.client.control.QuestionClientController;
import hsts.client.net.HSTSClient;
import hsts.common.QuestionDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class QuestionBankPageController {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5555;
    private static final String FIXED_TYPE = "MULTIPLE_CHOICE";

    private final ObservableList<QuestionDTO> questions = FXCollections.observableArrayList();

    private QuestionClientController questionClientController;
    private HSTSClient client;

    @FXML private TableView<QuestionDTO> tableView;
    @FXML private TableColumn<QuestionDTO, Number> idColumn;
    @FXML private TableColumn<QuestionDTO, String> topicColumn;
    @FXML private TableColumn<QuestionDTO, String> difficultyColumn;
    @FXML private TableColumn<QuestionDTO, String> statusColumn;

    @FXML private TextArea contentArea;
    @FXML private TextArea activityLogArea;

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

    @FXML private Label statusLabel;
    @FXML private Label serverStatusValue;
    @FXML private Label databaseStatusValue;
    @FXML private Label questionsCountValue;
    @FXML private Label currentActionValue;
    @FXML private Label selectedIdValue;
    @FXML private Label selectedTypeValue;

    private ToggleGroup correctAnswerGroup;

    @FXML
    private void initialize() {
        setupComboBoxes();
        setupCorrectAnswerRadios();
        setupTable();

        connectToServer();
        updateConnectionLabels();

        addLog("FXML client GUI started.");

        if (isConnected()) {
            addLog("Connected to server at " + SERVER_HOST + ":" + SERVER_PORT + ".");
            loadQuestions();
        } else {
            setStatus("Cannot connect to server. Start the server first.");
            setCurrentAction("Waiting for server");
            addLog("Server connection failed. Start the server and reopen the client.");
        }
    }

    private void setupComboBoxes() {
        difficultyComboBox.setItems(FXCollections.observableArrayList("EASY", "MEDIUM", "HARD"));
        difficultyComboBox.setValue("EASY");

        statusComboBox.setItems(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        statusComboBox.setValue("ACTIVE");
    }

    private void setupCorrectAnswerRadios() {
        correctAnswerGroup = new ToggleGroup();

        option1Radio.setToggleGroup(correctAnswerGroup);
        option2Radio.setToggleGroup(correctAnswerGroup);
        option3Radio.setToggleGroup(correctAnswerGroup);
        option4Radio.setToggleGroup(correctAnswerGroup);

        option1Radio.setUserData(1);
        option2Radio.setUserData(2);
        option3Radio.setUserData(3);
        option4Radio.setUserData(4);
    }

    private void setupTable() {
        tableView.setItems(questions);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        idColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuestionId()));

        topicColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getTopic())));

        difficultyColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getDifficulty())));

        statusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(safe(data.getValue().getStatus())));

        tableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selectedQuestion) -> {
                    if (selectedQuestion != null) {
                        showSelectedQuestion(selectedQuestion);
                        setStatus("Question #" + selectedQuestion.getQuestionId() + " selected.");
                        setCurrentAction("Question selected");
                        addLog("Question #" + selectedQuestion.getQuestionId() + " selected from the question list.");
                    }
                }
        );
    }

    @FXML
    private void handleLoadQuestions() {
        loadQuestions();
    }

    @FXML
    private void handleUpdateQuestion() {
        updateSelectedQuestion();
    }

    @FXML
    private void handleRereadQuestion() {
        rereadSelectedQuestion();
    }

    @FXML
    private void handleClearSelection() {
        clearSelection();
    }

    private void connectToServer() {
        try {
            client = new HSTSClient(SERVER_HOST, SERVER_PORT);
            questionClientController = new QuestionClientController(client);
        } catch (Exception e) {
            client = null;
            questionClientController = null;
        }
    }

    private void loadQuestions() {
        if (!isConnected()) {
            setStatus("Cannot connect to server. Start the server first.");
            setCurrentAction("Server not connected");
            return;
        }

        setStatus("Loading questions from database...");
        setCurrentAction("Loading questions");
        addLog("Sending GET_ALL_QUESTIONS request to the server.");

        questionClientController.getAllQuestions()
                .thenAccept(loadedQuestions -> Platform.runLater(() -> {
                    questions.setAll(loadedQuestions);
                    questionsCountValue.setText(String.valueOf(loadedQuestions.size()));
                    setStatus("Loaded " + loadedQuestions.size() + " questions from database.");
                    setCurrentAction("Questions loaded");
                    addLog("Loaded " + loadedQuestions.size() + " questions from server/database.");
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        setStatus("Error while loading questions.");
                        setCurrentAction("Load failed");
                        addLog("Error while loading questions: " + getCleanError(error));
                    });
                    return null;
                });
    }

    private void updateSelectedQuestion() {
        if (!isConnected()) {
            setStatus("Cannot connect to server. Start the server first.");
            return;
        }

        QuestionDTO selectedQuestion = tableView.getSelectionModel().getSelectedItem();

        if (selectedQuestion == null) {
            setStatus("Select a question first.");
            return;
        }

        QuestionDTO updatedQuestion = buildUpdatedQuestion(selectedQuestion.getQuestionId());

        setStatus("Sending full update for question #" + updatedQuestion.getQuestionId() + "...");
        setCurrentAction("Updating question");
        addLog("Sending UPDATE_QUESTION request with content, attributes, options, illustration, and correct answer.");

        questionClientController.updateQuestion(updatedQuestion)
                .thenCompose(savedQuestion ->
                        questionClientController.getQuestionById(savedQuestion.getQuestionId()))
                .thenAccept(savedQuestion -> Platform.runLater(() -> {
                    replaceQuestionInTableAndSelect(savedQuestion);
                    showSelectedQuestion(savedQuestion);
                    setStatus("Question updated and re-read from database successfully.");
                    setCurrentAction("Update completed");
                    addLog("Question #" + savedQuestion.getQuestionId() + " updated and displayed.");
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        setStatus("Error while updating question.");
                        setCurrentAction("Update failed");
                        addLog("Error while updating question: " + getCleanError(error));
                    });
                    return null;
                });
    }

    private QuestionDTO buildUpdatedQuestion(int questionId) {
        int correctOptionNumber = getSelectedCorrectOptionNumber();

        return new QuestionDTO(
                questionId,
                contentArea.getText(),
                topicField.getText(),
                FIXED_TYPE,
                difficultyComboBox.getValue(),
                statusComboBox.getValue(),
                illustrationPathField.getText(),
                option1Field.getText(),
                option2Field.getText(),
                option3Field.getText(),
                option4Field.getText(),
                correctOptionNumber
        );
    }

    private int getSelectedCorrectOptionNumber() {
        if (correctAnswerGroup.getSelectedToggle() == null) {
            return 1;
        }

        Object userData = correctAnswerGroup.getSelectedToggle().getUserData();

        if (userData instanceof Integer) {
            return (Integer) userData;
        }

        return 1;
    }

    private void rereadSelectedQuestion() {
        if (!isConnected()) {
            setStatus("Cannot connect to server. Start the server first.");
            return;
        }

        QuestionDTO selectedQuestion = tableView.getSelectionModel().getSelectedItem();

        if (selectedQuestion == null) {
            setStatus("Select a question first.");
            return;
        }

        int questionId = selectedQuestion.getQuestionId();

        setStatus("Re-reading question #" + questionId + " from database...");
        setCurrentAction("Re-reading question");
        addLog("Sending GET_QUESTION_BY_ID request for question #" + questionId + ".");

        questionClientController.getQuestionById(questionId)
                .thenAccept(updatedQuestion -> Platform.runLater(() -> {
                    replaceQuestionInTableAndSelect(updatedQuestion);
                    showSelectedQuestion(updatedQuestion);
                    setStatus("Question #" + questionId + " re-read from database and displayed.");
                    setCurrentAction("Question re-read");
                    addLog("Question #" + questionId + " re-read from server/database.");
                }))
                .exceptionally(error -> {
                    Platform.runLater(() ->
                            setStatus("Error while re-reading question: " + getCleanError(error)));
                    return null;
                });
    }

    private void clearSelection() {
        tableView.getSelectionModel().clearSelection();

        selectedIdValue.setText("-");
        selectedTypeValue.setText(FIXED_TYPE);

        topicField.clear();
        illustrationPathField.clear();

        difficultyComboBox.setValue("EASY");
        statusComboBox.setValue("ACTIVE");

        contentArea.clear();

        option1Field.clear();
        option2Field.clear();
        option3Field.clear();
        option4Field.clear();

        correctAnswerGroup.selectToggle(null);

        setStatus("Selection cleared.");
        setCurrentAction("Selection cleared");
        addLog("Selection cleared.");
    }

    private void showSelectedQuestion(QuestionDTO question) {
        selectedIdValue.setText(String.valueOf(question.getQuestionId()));
        selectedTypeValue.setText(FIXED_TYPE);

        topicField.setText(safe(question.getTopic()));
        illustrationPathField.setText(safe(question.getIllustrationPath()));

        difficultyComboBox.setValue(defaultIfBlank(question.getDifficulty(), "EASY"));
        statusComboBox.setValue(defaultIfBlank(question.getStatus(), "ACTIVE"));

        contentArea.setText(safe(question.getContent()));

        option1Field.setText(safe(question.getAnswerOption1()));
        option2Field.setText(safe(question.getAnswerOption2()));
        option3Field.setText(safe(question.getAnswerOption3()));
        option4Field.setText(safe(question.getAnswerOption4()));

        selectCorrectAnswer(question.getCorrectOptionNumber());
    }

    private void selectCorrectAnswer(int correctOptionNumber) {
        if (correctOptionNumber == 1) {
            correctAnswerGroup.selectToggle(option1Radio);
        } else if (correctOptionNumber == 2) {
            correctAnswerGroup.selectToggle(option2Radio);
        } else if (correctOptionNumber == 3) {
            correctAnswerGroup.selectToggle(option3Radio);
        } else if (correctOptionNumber == 4) {
            correctAnswerGroup.selectToggle(option4Radio);
        } else {
            correctAnswerGroup.selectToggle(option1Radio);
        }
    }

    private void replaceQuestionInTableAndSelect(QuestionDTO updatedQuestion) {
        int updatedIndex = -1;

        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getQuestionId() == updatedQuestion.getQuestionId()) {
                questions.set(i, updatedQuestion);
                updatedIndex = i;
                break;
            }
        }

        if (updatedIndex == -1) {
            questions.add(updatedQuestion);
            updatedIndex = questions.size() - 1;
        }

        tableView.getSelectionModel().select(updatedIndex);
        tableView.scrollTo(updatedIndex);
    }

    private void updateConnectionLabels() {
        if (isConnected()) {
            serverStatusValue.setText("Connected to " + SERVER_HOST + ":" + SERVER_PORT);
            databaseStatusValue.setText("Server-side SQLite database");
            questionsCountValue.setText("0");
            currentActionValue.setText("Ready");
        } else {
            serverStatusValue.setText("Not connected");
            databaseStatusValue.setText("Unavailable until server starts");
            questionsCountValue.setText("0");
            currentActionValue.setText("Waiting for server");
        }
    }

    private boolean isConnected() {
        return questionClientController != null;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void setCurrentAction(String message) {
        if (currentActionValue != null) {
            currentActionValue.setText(message);
        }
    }

    private void addLog(String message) {
        if (activityLogArea == null) {
            return;
        }

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        activityLogArea.appendText("[" + time + "] " + message + System.lineSeparator());
    }

    private String getCleanError(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }

        Throwable cause = error.getCause();

        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }

        if (error.getMessage() != null) {
            return error.getMessage();
        }

        return error.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    public void close() throws Exception {
        if (client != null) {
            client.close();
        }
    }
}