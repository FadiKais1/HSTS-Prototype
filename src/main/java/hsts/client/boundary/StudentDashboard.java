package hsts.client.boundary;

import hsts.client.net.ServerEventBus;
import hsts.client.control.NotificationClientController;
import hsts.client.net.Client;
import hsts.common.LoginResult;
import hsts.common.type.UserRole;
import hsts.server.entity.Student;
import javafx.fxml.FXML;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;

public class StudentDashboard {
    /** This screen's own push registration; closing it affects no other screen. */
    private ServerEventBus.Subscription eventSubscription;

    private void closeEventSubscription() {
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
        }
    }

    private Client client;
    private Student currentStudent;
    private List notifications;

    // COMPATIBILITY-ONLY: JavaFX state for the Assignment 3 dashboard boundary.
    private Stage stage;
    private LoginResult loginResult;
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Button logoutButton;
    @FXML
    private Button examExecutionButton;
    @FXML
    private Button publishedGradesButton;
    @FXML
    private Button courseBotButton;
    private Runnable logoutHandler;
    private Runnable examExecutionHandler;
    private Runnable publishedGradesHandler;
    private Runnable courseBotHandler;
    @FXML private Button notificationsButton;
    private Runnable notificationsHandler;
    private NotificationClientController notificationClientController;

    public void configure(Stage stage, Client client, LoginResult loginResult, Runnable logoutHandler) {
        configure(stage, client, loginResult, logoutHandler, null, null);
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable examExecutionHandler) {
        configure(stage, client, loginResult, logoutHandler,
                examExecutionHandler, null);
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable examExecutionHandler,
                          Runnable publishedGradesHandler) {
        configure(stage, client, loginResult, logoutHandler, examExecutionHandler,
                publishedGradesHandler, null);
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable examExecutionHandler,
                          Runnable publishedGradesHandler, Runnable courseBotHandler) {
        this.stage = Objects.requireNonNull(stage);
        this.client = Objects.requireNonNull(client);
        this.loginResult = Objects.requireNonNull(loginResult);
        this.logoutHandler = Objects.requireNonNull(logoutHandler);
        this.examExecutionHandler = examExecutionHandler;
        this.publishedGradesHandler = publishedGradesHandler;
        this.courseBotHandler = courseBotHandler;

        displayIdentity();
        showError("");
        updateActionState();
    }

    @FXML
    private void initialize() {
        showError("");
    }

    @FXML
    private void handleLogout() {
        if (logoutButton != null) {
            logoutButton.setDisable(true);
        }

        try {
            if (logoutHandler == null) {
                throw new IllegalStateException("Logout handler is not configured");
            }
            logoutHandler.run();
        } catch (RuntimeException exception) {
            if (logoutButton != null) {
                logoutButton.setDisable(false);
            }
            showError("Unable to log out");
        }
    }

    @FXML
    private void handleExamExecution() {
        if (loginResult == null || loginResult.getRole() != UserRole.STUDENT) {
            showError("Exam access is available only to students");
            return;
        }
        if (examExecutionHandler == null) {
            showError("Exam access is unavailable");
            return;
        }

        try {
            examExecutionHandler.run();
        } catch (RuntimeException exception) {
            showError("Unable to open exam access");
        }
    }

    @FXML
    private void handlePublishedGrades() {
        if (loginResult == null || loginResult.getRole() != UserRole.STUDENT) {
            showError("Published grades are available only to students");
            return;
        }
        if (publishedGradesHandler == null) {
            showError("Published grades are unavailable");
            return;
        }

        try {
            publishedGradesHandler.run();
        } catch (RuntimeException exception) {
            showError("Unable to open published grades");
        }
    }

    @FXML
    private void handleCourseBot() {
        if (loginResult == null || loginResult.getRole() != UserRole.STUDENT) {
            showError("Course Bot is available only to students");
            return;
        }
        if (courseBotHandler == null) {
            showError("Course Bot is unavailable");
            return;
        }
        try {
            courseBotHandler.run();
        } catch (RuntimeException exception) {
            showError("Unable to open Course Bot");
        }
    }

    public void configureNotifications(Runnable notificationsHandler) {
        this.notificationsHandler = Objects.requireNonNull(notificationsHandler);
        this.notificationClientController = new NotificationClientController(client);
        notificationsButton.setDisable(false);
        if (client != null) {
            this.eventSubscription =
                client.getServerEventBus().subscribe(this::onServerEvent);
        }
        refreshUnreadBadge();
    }

    /**
     * Refreshes the unread badge when the server reports a change that creates
     * notifications, so the count stays current without a manual refresh.
     */
    private void onServerEvent(ServerEvent event) {
        if (event == null) {
            return;
        }
        ServerEventType type = event.getType();
        if (type != ServerEventType.NOTIFICATION_CREATED
                && type != ServerEventType.GRADES_PUBLISHED
                && type != ServerEventType.EXAM_APPROVAL_CHANGED) {
            return;
        }
        Platform.runLater(this::refreshUnreadBadge);
    }

    private void refreshUnreadBadge() {
        if (notificationClientController == null || notificationsButton == null) {
            return;
        }
        notificationClientController.getUnreadCount().whenComplete((count, failure) ->
                Platform.runLater(() -> {
                    if (failure == null && notificationsButton != null) {
                        notificationsButton.setText("Notifications (" + count + ")");
                    }
                }));
    }

    @FXML
    private void handleNotifications() {
        if (notificationsHandler == null) {
            showError("Notifications are unavailable");
            return;
        }
        notificationsHandler.run();
    }

    private void displayIdentity() {
        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome, " + loginResult.getFullName());
        }
        if (roleLabel != null) {
            roleLabel.setText(loginResult.getRole().name());
        }
    }

    private void showError(String message) {
        if (errorLabel != null) {
            boolean hasMessage = message != null && !message.isBlank();
            errorLabel.setText(message == null ? "" : message);
            errorLabel.setManaged(hasMessage);
            errorLabel.setVisible(hasMessage);
        }
    }

    private void updateActionState() {
        if (examExecutionButton != null) {
            boolean student = loginResult != null
                    && loginResult.getRole() == UserRole.STUDENT;
            examExecutionButton.setVisible(student);
            examExecutionButton.setManaged(student);
            examExecutionButton.setDisable(!student || examExecutionHandler == null);
        }
        if (publishedGradesButton != null) {
            boolean student = loginResult != null
                    && loginResult.getRole() == UserRole.STUDENT;
            publishedGradesButton.setVisible(student);
            publishedGradesButton.setManaged(student);
            publishedGradesButton.setDisable(
                    !student || publishedGradesHandler == null
            );
        }
        if (courseBotButton != null) {
            boolean student = loginResult != null
                    && loginResult.getRole() == UserRole.STUDENT;
            courseBotButton.setVisible(student);
            courseBotButton.setManaged(student);
            courseBotButton.setDisable(!student || courseBotHandler == null);
        }
    }

    public void showAvailableExams() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showActiveExam(int executionId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showExamHistory() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void openExamExecutionPage(int executionId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showExamResults() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void openCourseBot(int courseId) {
        if (courseId <= 0) {
            throw new IllegalArgumentException("Course ID must be positive");
        }
        if (courseBotHandler == null) {
            throw new IllegalStateException("Course Bot is unavailable");
        }
        courseBotHandler.run();
    }

    public void showNotifications() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void markNotificationAsRead(int notificationId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void refreshNotifications() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
