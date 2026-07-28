package hsts.client.boundary;

import hsts.client.control.ExamExecutionClientController;
import hsts.client.net.Client;
import hsts.common.LoginResult;
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

    private final ObservableList<PublishedGradeSummaryDTO> grades =
            FXCollections.observableArrayList();

    private Stage stage;
    private Client client;
    private LoginResult loginResult;
    private Runnable backHandler;
    private ExamExecutionClientController executionController;
    private PublishedGradeDTO currentGrade;
    private boolean disposed = true;
    private boolean listBusy;
    private boolean detailBusy;
    private boolean suppressSelection;
    private long listRequestGeneration;
    private long detailRequestGeneration;

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

    @FXML
    private void initialize() {
        configureGradeTable();
        clearDetail();
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
        this.listBusy = false;
        this.detailBusy = false;
        this.currentGrade = null;
        this.listRequestGeneration++;
        this.detailRequestGeneration++;

        userLabel.setText(safe(loginResult.getFullName()));
        roleLabel.setText(loginResult.getRole().name());
        grades.clear();
        clearDetail();
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
                        loadPublishedGrade(selected.getSubmissionId());
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
        listBusy = true;
        detailBusy = false;
        clearDetail();
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
                        setStatus(cleanError(error, LIST_ERROR));
                        updateControlState();
                        return;
                    }

                    grades.setAll(preserveServerOrder(loaded));
                    PublishedGradeSummaryDTO selected =
                            selectGrade(preferredSubmissionId);
                    if (grades.isEmpty()) {
                        clearDetail();
                        setStatus("No published grades are available.");
                    } else if (selected == null) {
                        clearDetail();
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

    @FXML
    private void handleRefresh() {
        if (!isConfigured() || listBusy) {
            return;
        }
        PublishedGradeSummaryDTO selected =
                gradeTable.getSelectionModel().getSelectedItem();
        Integer preferredSubmissionId = selected == null
                ? null : selected.getSubmissionId();
        listRequestGeneration++;
        detailRequestGeneration++;
        loadPublishedGrades(preferredSubmissionId);
    }

    @FXML
    private void handleBack() {
        if (disposed || backHandler == null) {
            setStatus("Back navigation is unavailable.");
            return;
        }

        Runnable navigation = backHandler;
        disposed = true;
        listRequestGeneration++;
        detailRequestGeneration++;
        listBusy = false;
        detailBusy = false;
        try {
            navigation.run();
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
                loadPublishedGrade(submissionId);
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

    private void updateControlState() {
        boolean configured = isConfigured();
        boolean busy = listBusy || detailBusy;
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

    private static String formatDecimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
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
