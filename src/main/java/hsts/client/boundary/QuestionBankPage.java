// package hsts.client.boundary;

// import hsts.client.control.QuestionClientController;
// import hsts.client.net.HSTSClient;
// import hsts.common.QuestionDTO;
// import javafx.application.Application;
// import javafx.application.Platform;
// import javafx.beans.property.SimpleIntegerProperty;
// import javafx.beans.property.SimpleStringProperty;
// import javafx.collections.FXCollections;
// import javafx.collections.ObservableList;
// import javafx.geometry.Insets;
// import javafx.scene.Scene;
// import javafx.scene.control.Button;
// import javafx.scene.control.Label;
// import javafx.scene.control.TableColumn;
// import javafx.scene.control.TableView;
// import javafx.scene.control.TextArea;
// import javafx.scene.layout.BorderPane;
// import javafx.scene.layout.HBox;
// import javafx.scene.layout.VBox;
// import javafx.stage.Stage;

// public class QuestionBankPage extends Application {
//     private final ObservableList<QuestionDTO> questions = FXCollections.observableArrayList();

//     private QuestionClientController controller;
//     private HSTSClient client;

//     private TableView<QuestionDTO> tableView;
//     private TextArea contentArea;
//     private Label statusLabel;

//     @Override
//     public void start(Stage stage) {
//         connectToServer();

//         BorderPane root = new BorderPane();
//         root.setPadding(new Insets(12));

//         Label title = new Label("HSTS Prototype - Question Bank");
//         title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

//         tableView = buildQuestionsTable();
//         contentArea = new TextArea();
//         contentArea.setPromptText("Select a question, edit its content, then click Update Question.");
//         contentArea.setWrapText(true);
//         contentArea.setPrefRowCount(6);

//         Button loadButton = new Button("Load Questions");
//         loadButton.setOnAction(event -> loadQuestions());

//         Button updateButton = new Button("Update Question");
//         updateButton.setOnAction(event -> updateSelectedQuestion());

//         statusLabel = new Label("Ready");

//         HBox buttons = new HBox(10, loadButton, updateButton);
//         VBox top = new VBox(10, title, buttons, statusLabel);
//         VBox center = new VBox(10, tableView, new Label("Question Content:"), contentArea);

//         root.setTop(top);
//         root.setCenter(center);

//         Scene scene = new Scene(root, 950, 620);
//         stage.setTitle("HSTS Question Bank Prototype");
//         stage.setScene(scene);
//         stage.show();

//         loadQuestions();
//     }

//     private void connectToServer() {
//         try {
//             client = new HSTSClient("localhost", 5555);
//             controller = new QuestionClientController(client);
//         } catch (Exception e) {
//             controller = null;
//             client = null;
//         }
//     }

//     private TableView<QuestionDTO> buildQuestionsTable() {
//         TableView<QuestionDTO> table = new TableView<>(questions);

//         TableColumn<QuestionDTO, Number> idColumn = new TableColumn<>("ID");
//         idColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getQuestionId()));
//         idColumn.setPrefWidth(70);

//         TableColumn<QuestionDTO, String> contentColumn = new TableColumn<>("Content");
//         contentColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getContent()));
//         contentColumn.setPrefWidth(430);

//         TableColumn<QuestionDTO, String> topicColumn = new TableColumn<>("Topic");
//         topicColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTopic()));
//         topicColumn.setPrefWidth(140);

//         TableColumn<QuestionDTO, String> typeColumn = new TableColumn<>("Type");
//         typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType()));
//         typeColumn.setPrefWidth(140);

//         TableColumn<QuestionDTO, String> difficultyColumn = new TableColumn<>("Difficulty");
//         difficultyColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDifficulty()));
//         difficultyColumn.setPrefWidth(120);

//         table.getColumns().add(idColumn);
//         table.getColumns().add(contentColumn);
//         table.getColumns().add(topicColumn);
//         table.getColumns().add(typeColumn);
//         table.getColumns().add(difficultyColumn);

//         table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selectedQuestion) -> {
//             if (selectedQuestion != null) {
//                 contentArea.setText(selectedQuestion.getContent());
//                 setStatus("Selected question #" + selectedQuestion.getQuestionId());
//             }
//         });

//         return table;
//     }

//     private void loadQuestions() {
//         if (!isConnected()) {
//             setStatus("Cannot connect to server. Start the server first.");
//             return;
//         }

//         setStatus("Loading questions...");
//         controller.getAllQuestions()
//                 .thenAccept(loadedQuestions -> Platform.runLater(() -> {
//                     questions.setAll(loadedQuestions);
//                     setStatus("Loaded " + loadedQuestions.size() + " questions.");
//                 }))
//                 .exceptionally(error -> {
//                     Platform.runLater(() -> setStatus("Error: " + error.getMessage()));
//                     return null;
//                 });
//     }

//     private void updateSelectedQuestion() {
//         if (!isConnected()) {
//             setStatus("Cannot connect to server. Start the server first.");
//             return;
//         }

//         QuestionDTO selectedQuestion = tableView.getSelectionModel().getSelectedItem();
//         if (selectedQuestion == null) {
//             setStatus("Select a question first.");
//             return;
//         }

//         String newContent = contentArea.getText();
//         setStatus("Updating question #" + selectedQuestion.getQuestionId() + "...");

//         controller.updateQuestion(selectedQuestion.getQuestionId(), newContent)
//                 .thenCompose(updatedQuestion -> controller.getQuestionById(updatedQuestion.getQuestionId()))
//                 .thenAccept(updatedQuestion -> Platform.runLater(() -> {
//                     replaceQuestionInTable(updatedQuestion);
//                     tableView.getSelectionModel().select(updatedQuestion);
//                     contentArea.setText(updatedQuestion.getContent());
//                     setStatus("Question updated and reloaded from database.");
//                 }))
//                 .exceptionally(error -> {
//                     Platform.runLater(() -> setStatus("Error: " + error.getMessage()));
//                     return null;
//                 });
//     }

//     private void replaceQuestionInTable(QuestionDTO updatedQuestion) {
//         for (int i = 0; i < questions.size(); i++) {
//             if (questions.get(i).getQuestionId() == updatedQuestion.getQuestionId()) {
//                 questions.set(i, updatedQuestion);
//                 return;
//             }
//         }
//         questions.add(updatedQuestion);
//     }

//     private boolean isConnected() {
//         return controller != null;
//     }

//     private void setStatus(String message) {
//         statusLabel.setText(message);
//     }

//     @Override
//     public void stop() throws Exception {
//         if (client != null) {
//             client.close();
//         }
//     }
// }

package hsts.client.boundary;

import hsts.client.control.QuestionClientController;
import hsts.client.net.HSTSClient;
import hsts.common.QuestionDTO;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.ScrollPane;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class QuestionBankPage extends Application {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5555;

    private final ObservableList<QuestionDTO> questions = FXCollections.observableArrayList();

    private QuestionClientController controller;
    private HSTSClient client;

    private TableView<QuestionDTO> tableView;
    private TextArea contentArea;
    private TextArea activityLogArea;

    private Label statusLabel;
    private Label serverStatusValue;
    private Label databaseStatusValue;
    private Label questionsCountValue;
    private Label currentActionValue;

    private Label selectedIdValue;
    private Label selectedTopicValue;
    private Label selectedTypeValue;
    private Label selectedDifficultyValue;
    private Label selectedStatusValue;

    @Override
    public void start(Stage stage) {
        connectToServer();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: #f4f7fb;");

        VBox page = new VBox(14);
        page.getChildren().addAll(
                buildHeader(),
                buildStatusPanel(),
                buildActionButtons(),
                buildMainArea(),
                buildActivityLog()
        );

        VBox.setVgrow(page.getChildren().get(3), Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.setStyle(
                "-fx-background: #f4f7fb;" +
                "-fx-background-color: #f4f7fb;"
        );

        root.setCenter(scrollPane);

        updateConnectionLabels();

        Scene scene = new Scene(root, 1250, 760);
        stage.setTitle("HSTS Exam Management System - Question Bank Prototype");
        stage.setScene(scene);
        stage.show();

        addLog("Client GUI started.");
        if (isConnected()) {
            addLog("Connected to server at " + SERVER_HOST + ":" + SERVER_PORT + ".");
            loadQuestions();
        } else {
            setStatus("Cannot connect to server. Start the server first.");
            setCurrentAction("Waiting for server");
            addLog("Server connection failed. Start the server and reopen the client.");
        }
    }

    private VBox buildHeader() {
        Label title = new Label("HSTS Exam Management System");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Question Bank Prototype");
        subtitle.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");

        Label description = new Label(
                "Prototype flow: load questions from the database, select a question by ID, edit its content, send update request to the server, then re-read and display the updated question."
        );
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 13px; -fx-text-fill: #4b5563;");

        VBox header = new VBox(4, title, subtitle, description);
        header.setPadding(new Insets(0, 0, 4, 0));
        return header;
    }

    private GridPane buildStatusPanel() {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        grid.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #dbe3ef;" +
                "-fx-border-radius: 12;"
        );

        Label panelTitle = new Label("Prototype Runtime Status");
        panelTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        serverStatusValue = createStatusValueLabel();
        databaseStatusValue = createStatusValueLabel();
        questionsCountValue = createStatusValueLabel();
        currentActionValue = createStatusValueLabel();

        grid.add(panelTitle, 0, 0, 4, 1);

        grid.add(createStatusTitleLabel("Server:"), 0, 1);
        grid.add(serverStatusValue, 1, 1);

        grid.add(createStatusTitleLabel("Database:"), 2, 1);
        grid.add(databaseStatusValue, 3, 1);

        grid.add(createStatusTitleLabel("Questions loaded:"), 0, 2);
        grid.add(questionsCountValue, 1, 2);

        grid.add(createStatusTitleLabel("Current action:"), 2, 2);
        grid.add(currentActionValue, 3, 2);

        return grid;
    }

    private HBox buildActionButtons() {
        Button loadButton = new Button("1. Load Questions");
        Button updateButton = new Button("2. Update Question");
        Button rereadButton = new Button("3. Re-read From Database");
        Button clearButton = new Button("Clear Selection");

        stylePrimaryButton(loadButton);
        styleSuccessButton(updateButton);
        styleNeutralButton(rereadButton);
        styleNeutralButton(clearButton);

        loadButton.setOnAction(event -> loadQuestions());
        updateButton.setOnAction(event -> updateSelectedQuestion());
        rereadButton.setOnAction(event -> rereadSelectedQuestion());
        clearButton.setOnAction(event -> clearSelection());

        HBox buttons = new HBox(10, loadButton, updateButton, rereadButton, clearButton);
        buttons.setAlignment(Pos.CENTER_LEFT);
        return buttons;
    }

    private SplitPane buildMainArea() {
        VBox leftPanel = buildQuestionListPanel();
        VBox rightPanel = buildEditPanel();

        SplitPane splitPane = new SplitPane(leftPanel, rightPanel);
        splitPane.setDividerPositions(0.38);
        splitPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        return splitPane;
    }

    private VBox buildQuestionListPanel() {
        Label title = new Label("Questions List");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label hint = new Label("Only question metadata is shown here. Click a row to view and edit the full question content.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        tableView = buildQuestionsTable();

        VBox panel = new VBox(10, title, hint, tableView);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #dbe3ef;" +
                "-fx-border-radius: 12;"
        );

        VBox.setVgrow(tableView, Priority.ALWAYS);
        return panel;
    }

    private TableView<QuestionDTO> buildQuestionsTable() {
        TableView<QuestionDTO> table = new TableView<>(questions);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-font-size: 13px;");

        TableColumn<QuestionDTO, Number> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getQuestionId()));
        idColumn.setMaxWidth(70);
        idColumn.setMinWidth(55);

        TableColumn<QuestionDTO, String> topicColumn = new TableColumn<>("Topic");
        topicColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getTopic())));

        TableColumn<QuestionDTO, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getType())));

        TableColumn<QuestionDTO, String> difficultyColumn = new TableColumn<>("Difficulty");
        difficultyColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getDifficulty())));

        TableColumn<QuestionDTO, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getStatus())));

        table.getColumns().add(idColumn);
        table.getColumns().add(topicColumn);
        table.getColumns().add(typeColumn);
        table.getColumns().add(difficultyColumn);
        table.getColumns().add(statusColumn);

        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selectedQuestion) -> {
            if (selectedQuestion != null) {
                showSelectedQuestion(selectedQuestion);
                setStatus("Question #" + selectedQuestion.getQuestionId() + " selected.");
                setCurrentAction("Question selected");
                addLog("Question #" + selectedQuestion.getQuestionId() + " selected from the question list.");
            }
        });

        return table;
    }

    private VBox buildEditPanel() {
        Label title = new Label("Selected Question Details");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label hint = new Label("The full question content appears here only after selecting a question from the list.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        GridPane details = new GridPane();
        details.setHgap(12);
        details.setVgap(8);
        details.setPadding(new Insets(8, 0, 0, 0));

        selectedIdValue = createDetailValueLabel("-");
        selectedTopicValue = createDetailValueLabel("-");
        selectedTypeValue = createDetailValueLabel("-");
        selectedDifficultyValue = createDetailValueLabel("-");
        selectedStatusValue = createDetailValueLabel("-");

        details.add(createDetailTitleLabel("Question ID:"), 0, 0);
        details.add(selectedIdValue, 1, 0);

        details.add(createDetailTitleLabel("Topic:"), 2, 0);
        details.add(selectedTopicValue, 3, 0);

        details.add(createDetailTitleLabel("Type:"), 0, 1);
        details.add(selectedTypeValue, 1, 1);

        details.add(createDetailTitleLabel("Difficulty:"), 2, 1);
        details.add(selectedDifficultyValue, 3, 1);

        details.add(createDetailTitleLabel("Status:"), 0, 2);
        details.add(selectedStatusValue, 1, 2);

        Label contentLabel = new Label("Question Content:");
        contentLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        contentArea = new TextArea();
        contentArea.setPromptText("Select a question from the left list. The full question content will appear here for editing.");
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(10);
        contentArea.setStyle(
                "-fx-font-size: 14px;" +
                "-fx-control-inner-background: #ffffff;" +
                "-fx-border-color: #cbd5e1;"
        );

        Label flowNote = new Label("Demo flow: Select question → Edit content → Update Question → Re-read From Database.");
        flowNote.setWrapText(true);
        flowNote.setStyle(
                "-fx-background-color: #eff6ff;" +
                "-fx-text-fill: #1d4ed8;" +
                "-fx-font-size: 12px;" +
                "-fx-padding: 8;" +
                "-fx-background-radius: 8;"
        );

        VBox panel = new VBox(10, title, hint, details, new Separator(), contentLabel, contentArea, flowNote);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #dbe3ef;" +
                "-fx-border-radius: 12;"
        );

        VBox.setVgrow(contentArea, Priority.ALWAYS);
        return panel;
    }

    private VBox buildActivityLog() {
        Label title = new Label("Activity Log");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");

        activityLogArea = new TextArea();
        activityLogArea.setEditable(false);
        activityLogArea.setWrapText(true);
        activityLogArea.setPrefRowCount(4);
        activityLogArea.setStyle(
                "-fx-font-family: Consolas;" +
                "-fx-font-size: 12px;" +
                "-fx-control-inner-background: #f8fafc;"
        );

        VBox panel = new VBox(8, title, statusLabel, activityLogArea);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #dbe3ef;" +
                "-fx-border-radius: 12;"
        );

        return panel;
    }

    private void connectToServer() {
        try {
            client = new HSTSClient(SERVER_HOST, SERVER_PORT);
            controller = new QuestionClientController(client);
        } catch (Exception e) {
            client = null;
            controller = null;
        }
    }

    private void loadQuestions() {
        if (!isConnected()) {
            setStatus("Cannot connect to server. Start the server first.");
            setCurrentAction("Server not connected");
            addLog("Cannot load questions because the server is not connected.");
            return;
        }

        setStatus("Loading questions from database...");
        setCurrentAction("Loading questions");
        addLog("Sending GET_ALL_QUESTIONS request to the server.");

        controller.getAllQuestions()
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
            setCurrentAction("Server not connected");
            addLog("Cannot update question because the server is not connected.");
            return;
        }

        QuestionDTO selectedQuestion = tableView.getSelectionModel().getSelectedItem();

        if (selectedQuestion == null) {
            setStatus("Select a question first.");
            setCurrentAction("No selected question");
            addLog("Update cancelled: no question selected.");
            return;
        }

        String newContent = contentArea.getText();

        if (newContent == null || newContent.trim().isEmpty()) {
            setStatus("Question content cannot be empty.");
            setCurrentAction("Invalid input");
            addLog("Update cancelled: question content is empty.");
            return;
        }

        int questionId = selectedQuestion.getQuestionId();

        setStatus("Sending update request for question #" + questionId + "...");
        setCurrentAction("Updating question");
        addLog("Sending UPDATE_QUESTION request for question #" + questionId + ".");

        controller.updateQuestion(questionId, newContent.trim())
                .thenCompose(updatedQuestion -> {
                    Platform.runLater(() -> addLog("Server confirmed update. Re-reading question #" + questionId + " from database."));
                    return controller.getQuestionById(updatedQuestion.getQuestionId());
                })
                .thenAccept(updatedQuestion -> Platform.runLater(() -> {
                    replaceQuestionInTableAndSelect(updatedQuestion);
                    showSelectedQuestion(updatedQuestion);
                    setStatus("Question updated and re-read from database successfully.");
                    setCurrentAction("Update completed");
                    addLog("Updated question #" + updatedQuestion.getQuestionId() + " received and displayed.");
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

    private void rereadSelectedQuestion() {
        if (!isConnected()) {
            setStatus("Cannot connect to server. Start the server first.");
            setCurrentAction("Server not connected");
            addLog("Cannot re-read question because the server is not connected.");
            return;
        }

        QuestionDTO selectedQuestion = tableView.getSelectionModel().getSelectedItem();

        if (selectedQuestion == null) {
            setStatus("Select a question first.");
            setCurrentAction("No selected question");
            addLog("Re-read cancelled: no question selected.");
            return;
        }

        int questionId = selectedQuestion.getQuestionId();

        setStatus("Re-reading question #" + questionId + " from database...");
        setCurrentAction("Re-reading question");
        addLog("Sending GET_QUESTION_BY_ID request for question #" + questionId + ".");

        controller.getQuestionById(questionId)
                .thenAccept(updatedQuestion -> Platform.runLater(() -> {
                    replaceQuestionInTableAndSelect(updatedQuestion);
                    showSelectedQuestion(updatedQuestion);
                    setStatus("Question #" + questionId + " re-read from database and displayed.");
                    setCurrentAction("Question re-read");
                    addLog("Question #" + questionId + " re-read from server/database.");
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        setStatus("Error while re-reading question.");
                        setCurrentAction("Re-read failed");
                        addLog("Error while re-reading question: " + getCleanError(error));
                    });
                    return null;
                });
    }

    private void clearSelection() {
        tableView.getSelectionModel().clearSelection();
        contentArea.clear();

        selectedIdValue.setText("-");
        selectedTopicValue.setText("-");
        selectedTypeValue.setText("-");
        selectedDifficultyValue.setText("-");
        selectedStatusValue.setText("-");

        setStatus("Selection cleared.");
        setCurrentAction("Selection cleared");
        addLog("Selection cleared.");
    }

    private void showSelectedQuestion(QuestionDTO question) {
        selectedIdValue.setText(String.valueOf(question.getQuestionId()));
        selectedTopicValue.setText(safe(question.getTopic()));
        selectedTypeValue.setText(safe(question.getType()));
        selectedDifficultyValue.setText(safe(question.getDifficulty()));
        selectedStatusValue.setText(safe(question.getStatus()));
        contentArea.setText(safe(question.getContent()));
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
        return controller != null;
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

    private Label createStatusTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #374151;");
        return label;
    }

    private Label createStatusValueLabel() {
        Label label = new Label("-");
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: #111827;");
        return label;
    }

    private Label createDetailTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4b5563;");
        return label;
    }

    private Label createDetailValueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #111827;");
        return label;
    }

    private void stylePrimaryButton(Button button) {
        button.setStyle(
                "-fx-background-color: #2563eb;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-background-radius: 8;"
        );
    }

    private void styleSuccessButton(Button button) {
        button.setStyle(
                "-fx-background-color: #059669;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-background-radius: 8;"
        );
    }

    private void styleNeutralButton(Button button) {
        button.setStyle(
                "-fx-background-color: #e5e7eb;" +
                "-fx-text-fill: #111827;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-background-radius: 8;"
        );
    }

    @Override
    public void stop() throws Exception {
        if (client != null) {
            client.close();
        }
    }
}