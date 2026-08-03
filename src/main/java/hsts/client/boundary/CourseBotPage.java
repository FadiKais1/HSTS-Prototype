package hsts.client.boundary;

import hsts.client.control.CourseBotClientController;
import hsts.client.net.Client;
import hsts.client.net.ServerEventBus;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
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
    /** This screen's own push registration; closing it affects no other screen. */
    private ServerEventBus.Subscription eventSubscription;

    /**
     * Set while the bot list is repopulated. Re-selecting a bot then hands the
     * combo a fresh object, which fires the action handler and would clear the
     * conversation even though the same bot is still chosen.
     */
    private boolean suppressBotSelection;

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
        // A teacher changing this course's bot, or activating a new one, changes
        // which bots this student may use.
        closeEventSubscription();
        eventSubscription = sharedClient.getServerEventBus().subscribe(
                this::onServerEvent, ServerEventType.COURSE_BOT_CHANGED
        );
        identityLabel.setText(loginResult.getFullName() + " · STUDENT");
        refreshBots();
    }

    @FXML
    private void initialize() {
        botBox.setCellFactory(ignored -> botCell());
        botBox.setButtonCell(botCell());
        botBox.setOnAction(event -> {
            if (!suppressBotSelection) {
                loadSelectedHistory();
            }
        });
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
        int courseId = bot.getCourseId();
        controller.askCourseBot(new AskCourseBotPayload(courseId, question))
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    if (disposed) return;
                    // Always clear the busy flag, even when this answer is no
                    // longer wanted. Leaving it set locked the Send button and
                    // was part of why an answer seemed to arrive a question late.
                    sending = false;
                    updateSendState();
                    if (failure != null) {
                        String message = message(failure, "Unable to obtain a Course Bot answer");
                        showStatus(message);
                        if (LOCKOUT.equals(message)) handleLockout();
                        return;
                    }
                    CourseBotSummaryDTO current = botBox.getValue();
                    if (current == null || current.getCourseId() != courseId) {
                        // She changed bot while waiting; her history for this one
                        // will be correct when she returns to it.
                        return;
                    }
                    questionArea.clear();
                    // The server returned the answer, so show it directly. Reloading
                    // the whole history instead meant a second round trip that could
                    // be discarded by any other refresh, leaving the answer to
                    // appear only when the next question arrived.
                    if (result != null && result.getMessage() != null) {
                        appendMessage(result.getMessage());
                        showStatus("");
                    } else {
                        loadSelectedHistory();
                    }
                }));
    }

    /**
     * Reacts to a teacher changing this course's bot.
     *
     * <p>Only the list of available bots is reloaded. The conversation is left
     * alone, since it belongs to this student and no teacher action changes
     * it.</p>
     */
    private void onServerEvent(ServerEvent event) {
        if (disposed || event == null
                || event.getType() != ServerEventType.COURSE_BOT_CHANGED) {
            return;
        }
        refreshBots();
    }

    private void closeEventSubscription() {
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
        }
    }

    private void refreshBots() {
        if (disposed) return;
        int generation = botGeneration.incrementAndGet();
        int selectedCourse = botBox.getValue() == null ? 0 : botBox.getValue().getCourseId();
        showStatus("Loading available Course Bots…");
        controller.getMyAvailableBots().whenComplete((bots, failure) -> Platform.runLater(() -> {
            if (disposed || generation != botGeneration.get()) return;
            if (failure != null) { showStatus(message(failure, "Unable to load Course Bots")); return; }
            boolean sameBot = bots.stream()
                    .anyMatch(bot -> bot.getCourseId() == selectedCourse);
            // Only a change of bot should discard the conversation on screen.
            suppressBotSelection = sameBot;
            try {
                botBox.setItems(FXCollections.observableArrayList(bots));
                CourseBotSummaryDTO chosen = bots.stream()
                        .filter(bot -> bot.getCourseId() == selectedCourse)
                        .findFirst().orElse(null);
                botBox.getSelectionModel().select(chosen);
            } finally {
                suppressBotSelection = false;
            }
            CourseBotSummaryDTO selected = botBox.getValue();
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

    /** Adds one exchange to the end of the conversation and scrolls to it. */
    private void appendMessage(BotMessageDTO message) {
        historyList.getItems().add(message);
        historyList.setPlaceholder(new Label("No personal Course Bot history yet"));
        historyList.scrollTo(historyList.getItems().size() - 1);
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
        closeEventSubscription();
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
