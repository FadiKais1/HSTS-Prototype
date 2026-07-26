package hsts.client.boundary;

import hsts.client.net.Client;
import hsts.common.LoginResult;
import hsts.common.type.UserRole;
import hsts.server.entity.Teacher;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class TeacherDashboard {
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
    private Label errorLabel;
    @FXML
    private Button logoutButton;
    private Runnable logoutHandler;
    @FXML
    private Button questionBankButton;
    @FXML
    private Button approvalRequestsButton;
    private Runnable questionBankHandler;

    public void configure(Stage stage, Client client, LoginResult loginResult, Runnable logoutHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        questionBankHandler = null;
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable questionBankHandler) {
        configureDashboard(stage, client, loginResult, logoutHandler);
        this.questionBankHandler = Objects.requireNonNull(questionBankHandler);
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

    private void configureDashboard(Stage stage, Client client, LoginResult loginResult,
                                    Runnable logoutHandler) {
        this.stage = Objects.requireNonNull(stage);
        this.client = Objects.requireNonNull(client);
        this.loginResult = Objects.requireNonNull(loginResult);
        this.logoutHandler = Objects.requireNonNull(logoutHandler);

        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome, " + loginResult.getFullName());
        }
        if (roleLabel != null) {
            roleLabel.setText(loginResult.getRole().name());
        }
        if (approvalRequestsButton != null) {
            boolean coordinator = loginResult.getRole() == UserRole.COORDINATOR;
            approvalRequestsButton.setManaged(coordinator);
            approvalRequestsButton.setVisible(coordinator);
        }
        showError("");
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
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
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
