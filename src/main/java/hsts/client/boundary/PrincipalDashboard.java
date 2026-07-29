package hsts.client.boundary;

import hsts.client.net.Client;
import hsts.common.LoginResult;
import hsts.server.entity.Principal;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.Objects;

public class PrincipalDashboard {
    private Client client;
    private Principal currentPrincipal;

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
    private Button reportsButton;
    private Runnable reportsHandler;
    @FXML
    private Button oversightButton;
    private Runnable oversightHandler;

    public void configure(Stage stage, Client client, LoginResult loginResult, Runnable logoutHandler) {
        configure(stage, client, loginResult, logoutHandler, null);
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable reportsHandler) {
        configure(stage, client, loginResult, logoutHandler, reportsHandler, null);
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable logoutHandler, Runnable reportsHandler,
                          Runnable oversightHandler) {
        this.stage = Objects.requireNonNull(stage);
        this.client = Objects.requireNonNull(client);
        this.loginResult = Objects.requireNonNull(loginResult);
        this.logoutHandler = Objects.requireNonNull(logoutHandler);
        this.reportsHandler = reportsHandler;
        this.oversightHandler = oversightHandler;

        displayIdentity();
        updateReportsState();
        updateOversightState();
        showError("");
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
    private void handleReports() {
        if (reportsHandler == null) {
            showError("Reports are unavailable");
            return;
        }
        try {
            reportsHandler.run();
        } catch (RuntimeException exception) {
            showError("Reports are unavailable");
        }
    }

    private void updateReportsState() {
        if (reportsButton != null) {
            reportsButton.setDisable(reportsHandler == null);
        }
    }

    @FXML
    private void handleOversight() {
        if (oversightHandler == null) {
            showError("Principal oversight is unavailable");
            return;
        }
        try {
            oversightHandler.run();
        } catch (RuntimeException exception) {
            showError("Principal oversight is unavailable");
        }
    }

    private void updateOversightState() {
        if (oversightButton != null) {
            oversightButton.setDisable(oversightHandler == null);
        }
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

    public void viewQuestions() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void viewExams() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void viewResults() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showTeacherExamsReport(int teacherId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showCourseExamsReport(int courseId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showStudentExamsReport(int studentId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showExamExecutionReport(int executionId) {
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
}
