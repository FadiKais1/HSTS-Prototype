package hsts.client.boundary;

import hsts.client.control.CourseBotClientController;
import hsts.client.net.Client;
import hsts.common.AskCourseBotPayload;
import hsts.common.BotHistoryDTO;
import hsts.common.BotMessageDTO;
import hsts.common.CourseBotSummaryDTO;
import hsts.common.LoginResult;
import hsts.common.type.BotAnswerStatus;
import hsts.common.type.UserRole;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class CourseBotPage {
    private static final String NO_ANSWER =
            "No suitable answer was found in the course material";
    private static final String LOCKOUT =
            "Course Bot is unavailable during an active exam";
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Stage stage;
    private LoginResult loginResult;
    private CourseBotClientController controller;
    private Runnable backHandler;
    private boolean disposed = true;
    private boolean sending;
    private boolean lockedOut;
    private final AtomicInteger botGeneration = new AtomicInteger();
    private final AtomicInteger historyGeneration = new AtomicInteger();

    @FXML private Label identityLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<CourseBotSummaryDTO> botBox;
    @FXML private ListView<BotMessageDTO> historyList;
    @FXML private TextArea questionArea;
    @FXML private Button sendButton;

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable backHandler) {
        this.stage = Objects.requireNonNull(stage, "stage");
        Client sharedClient = Objects.requireNonNull(client, "client");
        this.loginResult = Objects.requireNonNull(loginResult, "loginResult");
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        if (loginResult.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Course Bot is available only to students");
        }
        controller = new CourseBotClientController(sharedClient);
        disposed = false;
        identityLabel.setText(loginResult.getFullName() + " · STUDENT");
        refreshBots();
    }

    @FXML
    private void initialize() {
        botBox.setCellFactory(ignored -> botCell());
        botBox.setButtonCell(botCell());
        botBox.setOnAction(event -> loadSelectedHistory());
        historyList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(BotMessageDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String answer = item.getAnswerStatus() == BotAnswerStatus.NO_SUITABLE_ANSWER
                        ? NO_ANSWER : item.getAnswerText();
                setText("You: " + item.getQuestionText() + "\nCourse Bot: " + answer
                        + "\n" + TIME.format(item.getCreatedAt()));
                setWrapText(true);
            }
        });
        historyList.setPlaceholder(new Label("Select a Course Bot to view your history"));
        showStatus("");
        updateSendState();
    }

    private ListCell<CourseBotSummaryDTO> botCell() {
        return new ListCell<>() {
            @Override protected void updateItem(CourseBotSummaryDTO item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getCourseName() + " · " + item.getBotName());
            }
        };
    }

    @FXML private void handleBack() {
        dispose();
        backHandler.run();
    }

    @FXML private void handleRefresh() { refreshBots(); }

    @FXML private void handleSend() {
        CourseBotSummaryDTO bot = botBox.getValue();
        String question = questionArea.getText();
        if (bot == null) { showStatus("Select a Course Bot first"); return; }
        if (question == null || question.isBlank()) { showStatus("Enter a question"); return; }
        if (sending || lockedOut) return;
        sending = true;
        updateSendState();
        int generation = historyGeneration.get();
        controller.askCourseBot(new AskCourseBotPayload(bot.getCourseId(), question))
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    if (disposed || generation != historyGeneration.get()) return;
                    sending = false;
                    updateSendState();
                    if (failure != null) {
                        String message = message(failure, "Unable to obtain a Course Bot answer");
                        showStatus(message);
                        if (LOCKOUT.equals(message)) handleLockout();
                        return;
                    }
                    questionArea.clear();
                    loadSelectedHistory();
                }));
    }

    private void refreshBots() {
        if (disposed) return;
        int generation = botGeneration.incrementAndGet();
        int selectedCourse = botBox.getValue() == null ? 0 : botBox.getValue().getCourseId();
        showStatus("Loading available Course Bots…");
        controller.getMyAvailableBots().whenComplete((bots, failure) -> Platform.runLater(() -> {
            if (disposed || generation != botGeneration.get()) return;
            if (failure != null) { showStatus(message(failure, "Unable to load Course Bots")); return; }
            botBox.setItems(FXCollections.observableArrayList(bots));
            CourseBotSummaryDTO selected = bots.stream()
                    .filter(bot -> bot.getCourseId() == selectedCourse).findFirst().orElse(null);
            botBox.getSelectionModel().select(selected);
            if (bots.isEmpty()) {
                historyGeneration.incrementAndGet();
                historyList.getItems().clear();
                historyList.setPlaceholder(new Label("No Course Bots are currently available"));
                showStatus("No Course Bots are currently available");
            } else {
                showStatus("");
                if (selected == null) botBox.getSelectionModel().selectFirst();
            }
            updateSendState();
        }));
    }

    private void loadSelectedHistory() {
        if (disposed) return;
        int generation = historyGeneration.incrementAndGet();
        CourseBotSummaryDTO bot = botBox.getValue();
        lockedOut = false;
        historyList.getItems().clear();
        if (bot == null) { updateSendState(); return; }
        showStatus("Loading personal Bot history…");
        controller.getMyBotHistory(bot.getCourseId()).whenComplete((history, failure) ->
                Platform.runLater(() -> {
                    if (disposed || generation != historyGeneration.get()) return;
                    if (failure != null) {
                        String message = message(failure, "Unable to load Bot history");
                        showStatus(message);
                        if (LOCKOUT.equals(message)) handleLockout();
                        return;
                    }
                    displayHistory(history);
                    showStatus("");
                    updateSendState();
                }));
    }

    private void displayHistory(BotHistoryDTO history) {
        List<BotMessageDTO> messages = history.getMessages();
        historyList.setItems(FXCollections.observableArrayList(messages));
        historyList.setPlaceholder(new Label("No personal Course Bot history yet"));
        if (!messages.isEmpty()) historyList.scrollTo(messages.size() - 1);
    }

    private void handleLockout() {
        lockedOut = true;
        historyList.getItems().clear();
        updateSendState();
        refreshBots();
    }

    private void updateSendState() {
        sendButton.setDisable(disposed || sending || lockedOut || botBox.getValue() == null);
        questionArea.setDisable(disposed || lockedOut || botBox.getValue() == null);
    }

    public void showBotHistory(int courseId) {
        if (courseId <= 0) throw new IllegalArgumentException("Course ID must be positive");
        ensureConfigured();
        CourseBotSummaryDTO bot = botBox.getItems().stream()
                .filter(item -> item.getCourseId() == courseId).findFirst()
                .orElseThrow(() -> new IllegalStateException("Course Bot is unavailable"));
        botBox.getSelectionModel().select(bot);
        loadSelectedHistory();
    }

    public void sendQuestionToBot(int courseId, String questionText) {
        showBotHistory(courseId);
        questionArea.setText(questionText);
        handleSend();
    }

    public void displayBotAnswer(String answerText) {
        throw new IllegalStateException("Course Bot answers must come from authenticated history");
    }

    public void dispose() {
        disposed = true;
        botGeneration.incrementAndGet();
        historyGeneration.incrementAndGet();
    }

    private void ensureConfigured() {
        if (disposed || controller == null) throw new IllegalStateException("Course Bot page is not configured");
    }

    private void showStatus(String message) {
        statusLabel.setText(message == null ? "" : message);
        boolean visible = message != null && !message.isBlank();
        statusLabel.setVisible(visible);
        statusLabel.setManaged(visible);
    }

    private static String message(Throwable failure, String fallback) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank() ? fallback : cause.getMessage();
    }
}
