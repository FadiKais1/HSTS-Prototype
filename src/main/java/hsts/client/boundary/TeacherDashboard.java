package hsts.client.boundary;

import hsts.client.net.ServerEventBus;
import hsts.client.control.NotificationClientController;
import hsts.client.net.Client;
import hsts.common.LoginResult;
import hsts.common.type.UserRole;
import hsts.server.entity.Teacher;
import javafx.fxml.FXML;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class TeacherDashboard {
    /** This screen's own push registration; closing it affects no other screen. */
    private ServerEventBus.Subscription eventSubscription;

    private void closeEventSubscription() {
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
        }
    }

    private Client client;
    private Teacher currentTeacher;
    private List notifications;

    // COMPATIBILITY-ONLY: JavaFX state for the Assignment 3 dashboard boundary.
    private Stage stage;
    private LoginResult loginResult;
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label roleLabel;

    @FXML
    private Label dashboardTitleLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Button logoutButton;
    private Runnable logoutHandler;
    @FXML
    private Button questionBankButton;
    @FXML
    private Button approvalRequestsButton;
    private Runnable approvalRequestsHandler;
    private Runnable questionBankHandler;
    @FXML
    private Button examManagementButton;
    private Runnable examManagementHandler;
    @FXML
    private Button examSchedulingButton;
    private Runnable examSchedulingHandler;
    @FXML
    private Button gradeReviewButton;
    private Runnable gradeReviewHandler;
    @FXML
    private Button reportsButton;
    private Runnable reportsHandler;
    @FXML
    private Button courseBotsButton;
    private Runnable courseBotsHandler;
    @FXML private Button notificationsButton;
    private Runnable notificationsHandler;
    private NotificationClientController notificationClientController;

    public void configure(Stage stage, Client client, LoginResult loginResult, Runnable logoutHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        questionBankHandler = null;
        examManagementHandler = null;
        approvalRequestsHandler = null;
        examSchedulingHandler = null;
        gradeReviewHandler = null;
        reportsHandler = null;
        updateApprovalRequestsState();
        updateExamSchedulingState();
        updateGradeReviewState();
        updateReportsState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable questionBankHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        this.questionBankHandler = Objects.requireNonNull(questionBankHandler);
        examManagementHandler = null;
        approvalRequestsHandler = null;
        examSchedulingHandler = null;
        gradeReviewHandler = null;
        reportsHandler = null;
        updateApprovalRequestsState();
        updateExamSchedulingState();
        updateGradeReviewState();
        updateReportsState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable questionBankHandler,
                          Runnable examManagementHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        this.questionBankHandler = Objects.requireNonNull(questionBankHandler);
        this.examManagementHandler = Objects.requireNonNull(examManagementHandler);
        approvalRequestsHandler = null;
        examSchedulingHandler = null;
        gradeReviewHandler = null;
        reportsHandler = null;
        updateApprovalRequestsState();
        updateExamSchedulingState();
        updateGradeReviewState();
        updateReportsState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable questionBankHandler,
                          Runnable examManagementHandler, Runnable approvalRequestsHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        this.questionBankHandler = Objects.requireNonNull(questionBankHandler);
        this.examManagementHandler = Objects.requireNonNull(examManagementHandler);
        this.approvalRequestsHandler = Objects.requireNonNull(approvalRequestsHandler);
        examSchedulingHandler = null;
        gradeReviewHandler = null;
        reportsHandler = null;
        updateApprovalRequestsState();
        updateExamSchedulingState();
        updateGradeReviewState();
        updateReportsState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable questionBankHandler,
                          Runnable examManagementHandler, Runnable approvalRequestsHandler,
                          Runnable examSchedulingHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        this.questionBankHandler = Objects.requireNonNull(questionBankHandler);
        this.examManagementHandler = Objects.requireNonNull(examManagementHandler);
        this.approvalRequestsHandler = Objects.requireNonNull(approvalRequestsHandler);
        this.examSchedulingHandler = Objects.requireNonNull(examSchedulingHandler);
        gradeReviewHandler = null;
        reportsHandler = null;
        updateApprovalRequestsState();
        updateExamSchedulingState();
        updateGradeReviewState();
        updateReportsState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable questionBankHandler,
                          Runnable examManagementHandler, Runnable approvalRequestsHandler,
                          Runnable examSchedulingHandler, Runnable gradeReviewHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        this.questionBankHandler = Objects.requireNonNull(questionBankHandler);
        this.examManagementHandler = Objects.requireNonNull(examManagementHandler);
        this.approvalRequestsHandler = Objects.requireNonNull(approvalRequestsHandler);
        this.examSchedulingHandler = Objects.requireNonNull(examSchedulingHandler);
        this.gradeReviewHandler = Objects.requireNonNull(gradeReviewHandler);
        reportsHandler = null;
        updateApprovalRequestsState();
        updateExamSchedulingState();
        updateGradeReviewState();
        updateReportsState();
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable questionBankHandler,
                          Runnable examManagementHandler, Runnable approvalRequestsHandler,
                          Runnable examSchedulingHandler, Runnable gradeReviewHandler,
                          Runnable reportsHandler) {
        configure(stage, client, loginResult, logoutHandler, questionBankHandler,
                examManagementHandler, approvalRequestsHandler, examSchedulingHandler,
                gradeReviewHandler, reportsHandler, null);
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable questionBankHandler,
                          Runnable examManagementHandler, Runnable approvalRequestsHandler,
                          Runnable examSchedulingHandler, Runnable gradeReviewHandler,
                          Runnable reportsHandler, Runnable courseBotsHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        this.questionBankHandler = Objects.requireNonNull(questionBankHandler);
        this.examManagementHandler = Objects.requireNonNull(examManagementHandler);
        this.approvalRequestsHandler = Objects.requireNonNull(approvalRequestsHandler);
        this.examSchedulingHandler = Objects.requireNonNull(examSchedulingHandler);
        this.gradeReviewHandler = Objects.requireNonNull(gradeReviewHandler);
        this.reportsHandler = Objects.requireNonNull(reportsHandler);
        this.courseBotsHandler = courseBotsHandler;
        updateApprovalRequestsState();
        updateExamSchedulingState();
        updateGradeReviewState();
        updateReportsState();
        updateCourseBotsState();
    }

    @FXML
    private void handleCourseBots() {
        if (courseBotsHandler == null || loginResult == null
                || (loginResult.getRole() != UserRole.TEACHER
                && loginResult.getRole() != UserRole.COORDINATOR)) {
            showError("Course Bots are unavailable");
            return;
        }
        try {
            courseBotsHandler.run();
        } catch (RuntimeException exception) {
            showError("Course Bots are unavailable");
        }
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
    private void handleQuestionBank() {
        if (questionBankHandler == null) {
            showError("Question bank is unavailable");
            return;
        }

        try {
            questionBankHandler.run();
        } catch (RuntimeException exception) {
            showError("Question bank is unavailable");
        }
    }

    @FXML
    private void handleExamManagement() {
        if (examManagementHandler == null) {
            showError("Exam management is unavailable");
            return;
        }

        try {
            examManagementHandler.run();
        } catch (RuntimeException exception) {
            showError("Exam management is unavailable");
        }
    }

    @FXML
    private void handleApprovalRequests() {
        if (approvalRequestsHandler == null
                || loginResult == null
                || loginResult.getRole() != UserRole.COORDINATOR) {
            showError("Approval requests are unavailable");
            return;
        }

        try {
            approvalRequestsHandler.run();
        } catch (RuntimeException exception) {
            showError("Approval requests are unavailable");
        }
    }

    @FXML
    private void handleExamScheduling() {
        if (examSchedulingHandler == null
                || loginResult == null
                || (loginResult.getRole() != UserRole.TEACHER
                && loginResult.getRole() != UserRole.COORDINATOR)) {
            showError("Exam scheduling is unavailable");
            return;
        }

        try {
            examSchedulingHandler.run();
        } catch (RuntimeException exception) {
            showError("Exam scheduling is unavailable");
        }
    }

    @FXML
    private void handleGradeReview() {
        if (gradeReviewHandler == null
                || loginResult == null
                || (loginResult.getRole() != UserRole.TEACHER
                && loginResult.getRole() != UserRole.COORDINATOR)) {
            showError("Grade review is unavailable");
            return;
        }

        try {
            gradeReviewHandler.run();
        } catch (RuntimeException exception) {
            showError("Grade review is unavailable");
        }
    }

    @FXML
    private void handleReports() {
        if (reportsHandler == null
                || loginResult == null
                || (loginResult.getRole() != UserRole.TEACHER
                && loginResult.getRole() != UserRole.COORDINATOR)) {
            showError("Reports are unavailable");
            return;
        }

        try {
            reportsHandler.run();
        } catch (RuntimeException exception) {
            showError("Reports are unavailable");
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
        refreshNotificationCount();
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
        Platform.runLater(this::refreshNotificationCount);
    }

    @FXML
    private void handleNotifications() {
        if (notificationsHandler == null) {
            showError("Notifications are unavailable");
            return;
        }
        notificationsHandler.run();
    }

    private void refreshNotificationCount() {
        notificationClientController.getUnreadCount().whenComplete((count, failure) ->
                Platform.runLater(() -> {
                    if (failure == null && notificationsButton != null) {
                        notificationsButton.setText("Notifications (" + count + ")");
                    }
                }));
    }

    private void configureDashboard(Stage stage, Client client, LoginResult loginResult,
                                    Runnable logoutHandler) {
        this.stage = Objects.requireNonNull(stage);
        this.client = Objects.requireNonNull(client);
        this.loginResult = Objects.requireNonNull(loginResult);
        this.logoutHandler = Objects.requireNonNull(logoutHandler);
        this.courseBotsHandler = null;

        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome, " + loginResult.getFullName());
        }
        if (dashboardTitleLabel != null) {
            dashboardTitleLabel.setText(
                    loginResult.getRole() == UserRole.COORDINATOR
                            ? "Coordinator Dashboard"
                            : "Teacher Dashboard"
            );
        }
        if (roleLabel != null) {
            roleLabel.setText(loginResult.getRole().name());
        }
        updateApprovalRequestsState();
        updateExamSchedulingState();
        updateGradeReviewState();
        updateReportsState();
        updateCourseBotsState();
        showError("");
    }

    private void updateApprovalRequestsState() {
        if (approvalRequestsButton == null || loginResult == null) {
            return;
        }
        boolean coordinator = loginResult.getRole() == UserRole.COORDINATOR;
        approvalRequestsButton.setManaged(coordinator);
        approvalRequestsButton.setVisible(coordinator);
        approvalRequestsButton.setDisable(!coordinator || approvalRequestsHandler == null);
    }

    private void updateExamSchedulingState() {
        if (examSchedulingButton == null || loginResult == null) {
            return;
        }
        UserRole role = loginResult.getRole();
        boolean manager = role == UserRole.TEACHER || role == UserRole.COORDINATOR;
        examSchedulingButton.setManaged(manager);
        examSchedulingButton.setVisible(manager);
        examSchedulingButton.setDisable(!manager || examSchedulingHandler == null);
    }

    private void updateGradeReviewState() {
        if (gradeReviewButton == null || loginResult == null) {
            return;
        }
        UserRole role = loginResult.getRole();
        boolean manager = role == UserRole.TEACHER || role == UserRole.COORDINATOR;
        gradeReviewButton.setManaged(manager);
        gradeReviewButton.setVisible(manager);
        gradeReviewButton.setDisable(!manager || gradeReviewHandler == null);
    }

    private void updateReportsState() {
        if (reportsButton == null || loginResult == null) {
            return;
        }
        UserRole role = loginResult.getRole();
        boolean manager = role == UserRole.TEACHER || role == UserRole.COORDINATOR;
        reportsButton.setManaged(manager);
        reportsButton.setVisible(manager);
        reportsButton.setDisable(!manager || reportsHandler == null);
    }

    private void updateCourseBotsState() {
        if (courseBotsButton == null || loginResult == null) {
            return;
        }
        UserRole role = loginResult.getRole();
        boolean manager = role == UserRole.TEACHER || role == UserRole.COORDINATOR;
        courseBotsButton.setManaged(manager);
        courseBotsButton.setVisible(manager);
        courseBotsButton.setDisable(!manager || courseBotsHandler == null);
    }

    private void showError(String message) {
        if (errorLabel != null) {
            boolean hasMessage = message != null && !message.isBlank();
            errorLabel.setText(message == null ? "" : message);
            errorLabel.setManaged(hasMessage);
            errorLabel.setVisible(hasMessage);
        }
    }

    public void showTeacherCourses() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showQuestionBank(int courseId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showCreatedExams() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void openExamExecution(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showExamExecutionReport(int executionId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void openExamCreation(int courseId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
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

    public void showApprovalRequests() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void checkCoordinatorPermissions() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showBotStatistics(int courseId) {
        if (courseId <= 0) {
            throw new IllegalArgumentException("Course ID must be positive");
        }
        if (courseBotsHandler == null) {
            throw new IllegalStateException("Course Bots are unavailable");
        }
        courseBotsHandler.run();
    }

    public void submitExamForApproval(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void scheduleApprovedExam(int examId, LocalDateTime openingTime, LocalDateTime closingTime) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void reviewExam(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void approveExam(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void rejectExam(int examId, String reason) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void exportReportToPDF(int reportId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void exportReportToExcel(int reportId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showPendingExams() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showTeacherExamsReport(int teacherId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
