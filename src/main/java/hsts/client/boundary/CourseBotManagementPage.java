package hsts.client.boundary;

import hsts.client.control.CourseBotClientController;
import hsts.client.control.QuestionClientController;
import hsts.client.net.Client;
import hsts.client.net.ServerEventBus;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import hsts.common.AddBotQuestionSourcesPayload;
import hsts.common.AddBotTextSourcePayload;
import hsts.common.EditBotSourcePayload;
import hsts.common.BotQuestionVersionReference;
import hsts.common.BotSourceDTO;
import hsts.common.BotUsageSummaryDTO;
import hsts.common.CommonBotQuestionDTO;
import hsts.common.CourseBotSummaryDTO;
import hsts.common.CourseSummaryDTO;
import hsts.common.CreateCourseBotPayload;
import hsts.common.LoginResult;
import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.RemoveBotSourcePayload;
import hsts.common.UpdateCourseBotPayload;
import hsts.common.UploadBotSourcePayload;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;
import hsts.common.type.BotStatus;
import hsts.common.type.QuestionStatus;
import hsts.common.type.UserRole;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class CourseBotManagementPage {
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Stage stage;
    private LoginResult loginResult;
    private CourseBotClientController botController;
    private QuestionClientController questionController;
    private Runnable backHandler;
    /** This screen's own push registration; closing it affects no other screen. */
    private ServerEventBus.Subscription eventSubscription;

    /**
     * Set when a colleague changed the bot while this teacher had text typed
     * into the source editor. The reload waits until that text is clear so her
     * work is never discarded.
     */
    private boolean deferredExternalRefresh;

    private boolean disposed = true;
    private boolean mutationActive;
    private final AtomicInteger refreshGeneration = new AtomicInteger();
    private final AtomicInteger detailGeneration = new AtomicInteger();

    @FXML private Label identityLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<CourseSummaryDTO> courseBox;
    @FXML private ComboBox<CourseBotSummaryDTO> botBox;
    @FXML private TextField botNameField;
    @FXML private CheckBox activeCheck;
    @FXML private Button createButton;
    @FXML private Button saveButton;
    @FXML private ListView<BotSourceDTO> sourceList;
    @FXML private TextField textDisplayNameField;
    @FXML private TextArea sourceTextArea;
    @FXML private Button editSourceButton;
    @FXML private Button saveSourceButton;
    @FXML private Button cancelEditButton;
    @FXML private Label editingSourceLabel;

    /** The source being revised, or null when the editor creates a new one. */
    private Integer editingSourceId;
    @FXML private Button addTextButton;
    @FXML private Button uploadButton;
    @FXML private ListView<QuestionDTO> questionList;
    @FXML private Button addQuestionsButton;
    @FXML private Button removeSourceButton;
    @FXML private Label usageLabel;
    @FXML private ListView<String> commonQuestionsList;

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable backHandler) {
        this.stage = Objects.requireNonNull(stage, "stage");
        Client sharedClient = Objects.requireNonNull(client, "client");
        this.loginResult = Objects.requireNonNull(loginResult, "loginResult");
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        UserRole role = loginResult.getRole();
        if (role != UserRole.TEACHER && role != UserRole.COORDINATOR) {
            throw new IllegalArgumentException("Course Bot management requires teacher or coordinator role");
        }
        botController = new CourseBotClientController(sharedClient);
        questionController = new QuestionClientController(sharedClient);
        disposed = false;
        identityLabel.setText(loginResult.getFullName() + " · " + role);
        // A course may be taught by several teachers and any of them may edit
        // the bot (requirement 45), so a colleague's change arrives here without
        // a manual refresh.
        closeEventSubscription();
        eventSubscription = sharedClient.getServerEventBus().subscribe(
                this::onServerEvent, ServerEventType.COURSE_BOT_CHANGED
        );
        refreshAll();
    }

    @FXML
    private void initialize() {
        courseBox.setCellFactory(ignored -> courseCell());
        courseBox.setButtonCell(courseCell());
        botBox.setCellFactory(ignored -> botCell());
        botBox.setButtonCell(botCell());
        sourceList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(BotSourceDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String reference = item.getQuestionId() == null ? ""
                        : " · Question " + item.getQuestionId() + " v" + item.getQuestionVersionNo();
                String removed = item.getRemovedAt() == null ? ""
                        : " · removed " + TIME.format(item.getRemovedAt());
                setText(item.getSourceType() + " · " + item.getDisplayName() + reference
                        + " · " + item.getStatus() + " · " + TIME.format(item.getCreatedAt()) + removed);
                setWrapText(true);
            }
        });
        questionList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        questionList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(QuestionDTO item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "#" + item.getQuestionId() + " v"
                        + item.getVersionNo() + " · " + item.getTopic() + " · "
                        + item.getDifficulty() + "\n" + item.getContent());
                setWrapText(true);
            }
        });
        courseBox.setOnAction(event -> selectCourse());
        botBox.setOnAction(event -> selectBot());
        sourceList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> updateActionState());
        showStatus("");
        updateActionState();
    }

    private ListCell<CourseSummaryDTO> courseCell() {
        return new ListCell<>() {
            @Override protected void updateItem(CourseSummaryDTO item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getCourseName() + " (" + item.getCourseCode() + ")");
            }
        };
    }

    private ListCell<CourseBotSummaryDTO> botCell() {
        return new ListCell<>() {
            @Override protected void updateItem(CourseBotSummaryDTO item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getBotName() + " · " + item.getStatus()
                        + " · " + item.getActiveSourceCount() + " sources · " + TIME.format(item.getUpdatedAt()));
            }
        };
    }

    @FXML private void handleBack() {
        dispose();
        backHandler.run();
    }

    @FXML private void handleRefresh() { refreshAll(); }

    /**
     * Reacts to another teacher creating, renaming or re-sourcing this course's
     * bot. Runs on the JavaFX thread; the bus marshals for us.
     */
    private void onServerEvent(ServerEvent event) {
        if (disposed || event == null
                || event.getType() != ServerEventType.COURSE_BOT_CHANGED) {
            return;
        }

        // A change of our own is in flight, or this teacher is part way through
        // typing a source. Reloading now would discard her text, so remember the
        // change and apply it once the editor is clear.
        if (mutationActive || hasUnsavedSourceText()) {
            deferredExternalRefresh = true;
            showStatus("Another teacher changed this bot. Your text is safe; "
                    + "the list will refresh once you add or clear it.");
            return;
        }

        deferredExternalRefresh = false;
        refreshAll();
    }

    private boolean hasUnsavedSourceText() {
        return (sourceTextArea != null && !sourceTextArea.getText().isBlank())
                || (textDisplayNameField != null
                        && !textDisplayNameField.getText().isBlank());
    }

    /** Applies a refresh held back while the source editor had text in it. */
    private void applyDeferredExternalRefresh() {
        if (deferredExternalRefresh && !disposed && !hasUnsavedSourceText()) {
            deferredExternalRefresh = false;
            refreshAll();
        }
    }

    private void closeEventSubscription() {
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
        }
    }

    private void refreshAll() {
        if (disposed) return;
        int generation = refreshGeneration.incrementAndGet();
        showStatus("Loading assigned courses and Course Bots…");
        CompletableFuture<List<CourseSummaryDTO>> courses = questionController.getMyCourses();
        CompletableFuture<List<CourseBotSummaryDTO>> bots = botController.getMyCourseBots();
        courses.thenCombine(bots, RefreshData::new).whenComplete((data, failure) ->
                Platform.runLater(() -> {
                    if (disposed || generation != refreshGeneration.get()) return;
                    if (failure != null) { showStatus(message(failure, "Unable to load Course Bots")); return; }
                    int selectedCourseId = selectedCourseId();
                    courseBox.setItems(FXCollections.observableArrayList(data.courses));
                    botBox.setItems(FXCollections.observableArrayList(data.bots));
                    if (!selectCourseById(selectedCourseId) && !data.courses.isEmpty()) {
                        courseBox.getSelectionModel().selectFirst();
                    }
                    selectCourse();
                    showStatus("");
                }));
    }

    private void selectCourse() {
        if (disposed) return;
        CourseSummaryDTO course = courseBox.getValue();
        CourseBotSummaryDTO matching = null;
        if (course != null) {
            for (CourseBotSummaryDTO bot : botBox.getItems()) {
                if (bot.getCourseId() == course.getCourseId()) { matching = bot; break; }
            }
        }
        botBox.getSelectionModel().select(matching);
        selectBot();
    }

    private void selectBot() {
        int generation = detailGeneration.incrementAndGet();
        CourseBotSummaryDTO bot = botBox.getValue();
        clearDetails();
        if (bot == null) { updateActionState(); return; }
        botNameField.setText(bot.getBotName());
        activeCheck.setSelected(bot.getStatus() == BotStatus.ACTIVE);
        updateActionState();
        botController.getBotSources(bot.getBotId()).whenComplete((sources, failure) ->
                Platform.runLater(() -> {
                    if (stale(generation)) return;
                    if (failure != null) { showStatus(message(failure, "Unable to load Bot sources")); return; }
                    sourceList.setItems(FXCollections.observableArrayList(sources));
                    updateActionState();
                }));
        botController.getBotUsage(bot.getBotId()).whenComplete((usage, failure) ->
                Platform.runLater(() -> {
                    if (stale(generation)) return;
                    if (failure != null) { showStatus(message(failure, "Unable to load Bot usage")); return; }
                    displayUsage(usage);
                }));
        QuestionFilterPayload filter = new QuestionFilterPayload(bot.getCourseId(), null,
                null, null, QuestionStatus.ACTIVE);
        questionController.listQuestions(filter).whenComplete((questions, failure) ->
                Platform.runLater(() -> {
                    if (stale(generation)) return;
                    if (failure != null) { showStatus(message(failure, "Unable to load Question Bank")); return; }
                    questionList.setItems(FXCollections.observableArrayList(questions));
                }));
    }

    @FXML private void handleCreate() {
        CourseSummaryDTO course = courseBox.getValue();
        String name = botNameField.getText();
        if (course == null) { showStatus("Select an assigned course first"); return; }
        if (name == null || name.isBlank()) { showStatus("Bot name is required"); return; }
        mutate(botController.createCourseBot(new CreateCourseBotPayload(course.getCourseId(), name)),
                "Course Bot created", true);
    }

    @FXML private void handleSave() {
        CourseBotSummaryDTO bot = botBox.getValue();
        String name = botNameField.getText();
        if (bot == null) { showStatus("Select a Course Bot first"); return; }
        if (name == null || name.isBlank()) { showStatus("Bot name is required"); return; }
        BotStatus status = activeCheck.isSelected() ? BotStatus.ACTIVE : BotStatus.INACTIVE;
        mutate(botController.updateCourseBot(new UpdateCourseBotPayload(bot.getBotId(), name, status)),
                "Course Bot updated", true);
    }

    @FXML private void handleAddText() {
        CourseBotSummaryDTO bot = botBox.getValue();
        if (bot == null) { showStatus("Select a Course Bot first"); return; }
        String name = textDisplayNameField.getText();
        String text = sourceTextArea.getText();
        if (name == null || name.isBlank()) { showStatus("Source display name is required"); return; }
        if (text == null || text.isBlank()) { showStatus("Source text is required"); return; }
        mutate(botController.addTextSource(new AddBotTextSourcePayload(bot.getBotId(), name, text)),
                "Text source added", false, () -> {
                    textDisplayNameField.clear();
                    sourceTextArea.clear();
                    // The editor is free again, so a refresh held back to
                    // protect typed text can safely run.
                    applyDeferredExternalRefresh();
                });
    }

    @FXML private void handleUpload() {
        CourseBotSummaryDTO bot = botBox.getValue();
        if (bot == null) { showStatus("Select a Course Bot first"); return; }
        if (mutationActive) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Course Bot source");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Course material (*.txt, *.pdf, *.docx)", "*.txt", "*.TXT", "*.pdf", "*.PDF", "*.docx", "*.DOCX"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) return;
        String basename = selected.toPath().getFileName().toString();
        BotSourceType type = sourceType(basename);
        if (type == null) { showStatus("Unsupported source file type"); return; }
        long size = selected.length();
        if (size == 0) { showStatus("Selected source file is empty"); return; }
        if (size > MAX_UPLOAD_BYTES) { showStatus("Selected source file exceeds 5 MiB"); return; }
        setMutationActive(true);
        CompletableFuture.supplyAsync(() -> readFile(selected)).thenCompose(bytes -> {
            if (bytes.length == 0) throw new IllegalArgumentException("Selected source file is empty");
            if (bytes.length > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("Selected source file exceeds 5 MiB");
            return botController.uploadSource(new UploadBotSourcePayload(bot.getBotId(), basename, type, bytes));
        }).whenComplete((result, failure) -> Platform.runLater(() -> finishMutation(
                result, failure, "File source added", false, null)));
    }

    @FXML private void handleAddQuestions() {
        CourseBotSummaryDTO bot = botBox.getValue();
        if (bot == null) { showStatus("Select a Course Bot first"); return; }
        List<QuestionDTO> selected = new ArrayList<>(questionList.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) { showStatus("Select one or more questions"); return; }
        List<BotQuestionVersionReference> references = selected.stream()
                .map(question -> new BotQuestionVersionReference(question.getQuestionId(), question.getVersionNo()))
                .toList();
        mutate(botController.addQuestionSources(new AddBotQuestionSourcesPayload(bot.getBotId(), references)),
                "Question sources added", false);
    }

    /**
     * Loads the selected source into the text editor so it can be revised.
     *
     * <p>The text is fetched on demand because source listings omit it.</p>
     */
    @FXML private void handleEditSource() {
        CourseBotSummaryDTO bot = botBox.getValue();
        BotSourceDTO source = sourceList.getSelectionModel().getSelectedItem();
        if (bot == null) { showStatus("Select a Course Bot first"); return; }
        if (source == null || source.getStatus() != BotSourceStatus.ACTIVE) {
            showStatus("Select an active source first"); return;
        }

        showStatus("Loading source text...");
        botController.getSourceText(
                new RemoveBotSourcePayload(bot.getBotId(), source.getSourceId())
        ).whenComplete((text, error) -> Platform.runLater(() -> {
            if (disposed) return;
            if (error != null) {
                showStatus(message(error, "Could not load the source text"));
                return;
            }
            editingSourceId = source.getSourceId();
            textDisplayNameField.setText(source.getDisplayName());
            sourceTextArea.setText(text);
            showEditingState(source.getDisplayName());
            showStatus("Editing “" + source.getDisplayName()
                    + "”. Save changes to replace it.");
        }));
    }

    /**
     * Replaces the source being edited.
     *
     * <p>The server removes the current source and adds the revision, so the
     * change is credited to this teacher and the previous text stays in the
     * record. If a colleague edited the same source first the request fails and
     * the typed text is left alone so it can be reapplied.</p>
     */
    @FXML private void handleSaveSource() {
        CourseBotSummaryDTO bot = botBox.getValue();
        if (bot == null) { showStatus("Select a Course Bot first"); return; }
        if (editingSourceId == null) { showStatus("No source is being edited"); return; }

        String name = textDisplayNameField.getText();
        String text = sourceTextArea.getText();
        if (name == null || name.isBlank()) { showStatus("Display name is required"); return; }
        if (text == null || text.isBlank()) { showStatus("Source text is required"); return; }

        mutate(botController.editSource(new EditBotSourcePayload(
                        bot.getBotId(), editingSourceId, name, text)),
                "Source updated", false, this::clearEditingState);
    }

    @FXML private void handleCancelEdit() {
        clearEditingState();
        showStatus("Edit cancelled.");
    }

    private void showEditingState(String displayName) {
        editingSourceLabel.setText("Editing an existing source: " + displayName);
        toggle(editingSourceLabel, true);
        toggle(saveSourceButton, true);
        toggle(cancelEditButton, true);
        toggle(addTextButton, false);
    }

    private void clearEditingState() {
        editingSourceId = null;
        textDisplayNameField.clear();
        sourceTextArea.clear();
        toggle(editingSourceLabel, false);
        toggle(saveSourceButton, false);
        toggle(cancelEditButton, false);
        toggle(addTextButton, true);
        applyDeferredExternalRefresh();
    }

    private static void toggle(javafx.scene.Node node, boolean visible) {
        if (node == null) return;
        node.setManaged(visible);
        node.setVisible(visible);
    }

    @FXML private void handleRemoveSource() {
        CourseBotSummaryDTO bot = botBox.getValue();
        BotSourceDTO source = sourceList.getSelectionModel().getSelectedItem();
        if (bot == null) { showStatus("Select a Course Bot first"); return; }
        if (source == null || source.getStatus() != BotSourceStatus.ACTIVE) {
            showStatus("Select an active source first"); return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove “" + source.getDisplayName() + "” from this Course Bot?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.initOwner(stage);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        mutate(botController.removeSource(new RemoveBotSourcePayload(bot.getBotId(), source.getSourceId())),
                "Source removed", false);
    }

    private <T> void mutate(CompletableFuture<T> operation, String success, boolean refreshAll) {
        mutate(operation, success, refreshAll, null);
    }

    private <T> void mutate(CompletableFuture<T> operation, String success, boolean all,
                            Runnable successAction) {
        if (mutationActive) return;
        setMutationActive(true);
        operation.whenComplete((result, failure) -> Platform.runLater(() ->
                finishMutation(result, failure, success, all, successAction)));
    }

    private void finishMutation(Object result, Throwable failure, String success,
                                boolean all, Runnable successAction) {
        if (disposed) return;
        setMutationActive(false);
        if (failure != null) { showStatus(message(failure, "Request failed")); return; }
        if (successAction != null) successAction.run();
        showStatus(success);
        if (all) refreshAll(); else { selectBot(); refreshBotSummaries(); }
    }

    private void refreshBotSummaries() {
        botController.getMyCourseBots().whenComplete((bots, failure) -> Platform.runLater(() -> {
            if (disposed || failure != null) return;
            int selected = botBox.getValue() == null ? 0 : botBox.getValue().getBotId();
            botBox.setItems(FXCollections.observableArrayList(bots));
            bots.stream().filter(bot -> bot.getBotId() == selected).findFirst()
                    .ifPresent(bot -> botBox.getSelectionModel().select(bot));
        }));
    }

    private void displayUsage(BotUsageSummaryDTO usage) {
        usageLabel.setText(usage.getBotName() + " · " + usage.getCourseName()
                + "\nTotal questions: " + usage.getTotalQuestions()
                + " · Last activity: " + (usage.getLastActivityAt() == null ? "N/A" : TIME.format(usage.getLastActivityAt())));
        List<String> common = usage.getCommonQuestions().stream()
                .map(this::formatCommonQuestion).toList();
        commonQuestionsList.setItems(FXCollections.observableArrayList(common));
        if (common.isEmpty()) commonQuestionsList.setPlaceholder(new Label("No common questions yet"));
    }

    private String formatCommonQuestion(CommonBotQuestionDTO question) {
        return question.getQuestionText() + " · " + question.getOccurrenceCount();
    }

    private void clearDetails() {
        sourceList.getItems().clear();
        questionList.getItems().clear();
        commonQuestionsList.getItems().clear();
        usageLabel.setText("Select a Course Bot to view anonymous usage");
    }

    private void updateActionState() {
        CourseBotSummaryDTO bot = botBox.getValue();
        boolean hasBot = bot != null;
        createButton.setDisable(mutationActive || courseBox.getValue() == null || hasBot);
        saveButton.setDisable(mutationActive || !hasBot);
        addTextButton.setDisable(mutationActive || !hasBot);
        uploadButton.setDisable(mutationActive || !hasBot);
        addQuestionsButton.setDisable(mutationActive || !hasBot);
        BotSourceDTO source = sourceList.getSelectionModel().getSelectedItem();
        removeSourceButton.setDisable(mutationActive || !hasBot || source == null
                || source.getStatus() != BotSourceStatus.ACTIVE);
    }

    private void setMutationActive(boolean active) {
        mutationActive = active;
        updateActionState();
    }

    private boolean stale(int generation) {
        return disposed || generation != detailGeneration.get();
    }

    private int selectedCourseId() {
        return courseBox.getValue() == null ? 0 : courseBox.getValue().getCourseId();
    }

    private boolean selectCourseById(int courseId) {
        for (CourseSummaryDTO course : courseBox.getItems()) {
            if (course.getCourseId() == courseId) {
                courseBox.getSelectionModel().select(course);
                return true;
            }
        }
        return false;
    }

    private void showStatus(String text) {
        statusLabel.setText(text == null ? "" : text);
        boolean visible = text != null && !text.isBlank();
        statusLabel.setManaged(visible);
        statusLabel.setVisible(visible);
    }

    public void dispose() {
        disposed = true;
        closeEventSubscription();
        refreshGeneration.incrementAndGet();
        detailGeneration.incrementAndGet();
    }

    private static byte[] readFile(File file) {
        try { return Files.readAllBytes(file.toPath()); }
        catch (IOException exception) { throw new IllegalStateException("Unable to read selected source file", exception); }
    }

    static BotSourceType sourceType(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt")) return BotSourceType.TXT;
        if (lower.endsWith(".pdf")) return BotSourceType.PDF;
        if (lower.endsWith(".docx")) return BotSourceType.DOCX;
        return null;
    }

    private static String message(Throwable failure, String fallback) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank() ? fallback : cause.getMessage();
    }

    private record RefreshData(List<CourseSummaryDTO> courses,
                               List<CourseBotSummaryDTO> bots) { }
}
