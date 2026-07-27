package hsts.client;

import hsts.client.boundary.LoginPage;
import hsts.client.boundary.ApprovalRequestsPage;
import hsts.client.boundary.ExamBuilderPage;
import hsts.client.boundary.PrincipalDashboard;
import hsts.client.boundary.QuestionBankPageController;
import hsts.client.boundary.StudentDashboard;
import hsts.client.boundary.TeacherDashboard;
import hsts.client.navigation.SceneNavigator;
import hsts.client.net.Client;
import hsts.common.LoginResult;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.type.UserRole;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MainClient extends Application {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5555;

    private Stage stage;
    private Client client;
    private String serverHost;
    private int serverPort;
    private LoginResult currentLogin;
    private boolean logoutInProgress;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        try {
            serverHost = resolveHost();
            serverPort = resolvePort();
            client = new Client(serverHost, serverPort);
            showLogin(null);
        } catch (IllegalArgumentException exception) {
            failStartup(exception.getMessage());
        } catch (IOException exception) {
            failStartup("Unable to connect to HSTS server at " + serverHost + ":" + serverPort);
        } catch (RuntimeException exception) {
            failStartup("Unable to start the HSTS client");
        }
    }

    @Override
    public void stop() {
        closeQuietly(client);
        client = null;
    }

    private String resolveHost() {
        String host = resolveSetting("hsts.server.host", "HSTS_SERVER_HOST", DEFAULT_HOST);
        if (host.isBlank()) {
            throw new IllegalArgumentException("Server host must not be blank");
        }
        return host;
    }

    private int resolvePort() {
        String configuredPort = resolveSetting(
                "hsts.server.port",
                "HSTS_SERVER_PORT",
                String.valueOf(DEFAULT_PORT)
        );

        final int port;
        try {
            port = Integer.parseInt(configuredPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Server port must be a number from 1 to 65535");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535");
        }
        return port;
    }

    private String resolveSetting(String propertyName, String environmentName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null) {
            return propertyValue.trim();
        }

        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null) {
            return environmentValue.trim();
        }

        return defaultValue.trim();
    }

    private void showLogin(String errorMessage) throws IOException {
        LoginPage loginPage = SceneNavigator.switchScene(
                stage,
                "/hsts/client/boundary/login-page.fxml",
                "HSTS Exam Management System - Login"
        );
        loginPage.configure(stage, client, this::openDashboard);
        if (errorMessage != null && !errorMessage.isBlank()) {
            loginPage.showLoginError(errorMessage);
        }
    }

    private void openDashboard(LoginResult loginResult) {
        currentLogin = loginResult;
        if (loginResult == null || loginResult.getRole() == null) {
            cleanupAfterNavigationFailure();
            return;
        }

        try {
            switch (loginResult.getRole()) {
                case STUDENT -> showStudentDashboard(loginResult);
                case TEACHER, COORDINATOR -> showTeacherDashboard(loginResult);
                case PRINCIPAL -> showPrincipalDashboard(loginResult);
            }
        } catch (IOException | RuntimeException exception) {
            cleanupAfterNavigationFailure();
        }
    }

    private void showStudentDashboard(LoginResult loginResult) throws IOException {
        StudentDashboard dashboard = SceneNavigator.switchScene(
                stage,
                "/hsts/client/boundary/student-dashboard.fxml",
                "HSTS Exam Management System - Student Dashboard"
        );
        dashboard.configure(stage, client, loginResult, this::logout);
    }

    private void showTeacherDashboard(LoginResult loginResult) throws IOException {
        TeacherDashboard dashboard = SceneNavigator.switchScene(
                stage,
                "/hsts/client/boundary/teacher-dashboard.fxml",
                "HSTS Exam Management System - Teacher Dashboard"
        );
        dashboard.configure(
                stage,
                client,
                loginResult,
                this::logout,
                () -> showQuestionBank(loginResult),
                () -> showExamBuilder(loginResult),
                () -> showApprovalRequests(loginResult)
        );
    }

    private void showPrincipalDashboard(LoginResult loginResult) throws IOException {
        PrincipalDashboard dashboard = SceneNavigator.switchScene(
                stage,
                "/hsts/client/boundary/principal-dashboard.fxml",
                "HSTS Exam Management System - Principal Dashboard"
        );
        dashboard.configure(stage, client, loginResult, this::logout);
    }

    private void showQuestionBank(LoginResult loginResult) {
        try {
            QuestionBankPageController controller = SceneNavigator.switchScene(
                    stage,
                    "/hsts/client/boundary/question-bank-page.fxml",
                    "HSTS Exam Management System - Question Bank"
            );
            controller.configure(client, () -> returnToTeacherDashboard(loginResult));
        } catch (IOException | RuntimeException exception) {
            try {
                showTeacherDashboard(loginResult);
                showNavigationError("Unable to open question bank");
            } catch (IOException | RuntimeException restoreException) {
                cleanupAfterNavigationFailure();
            }
        }
    }

    private void showExamBuilder(LoginResult loginResult) {
        try {
            ExamBuilderPage controller = SceneNavigator.switchScene(
                    stage,
                    "/hsts/client/boundary/exam-builder-page.fxml",
                    "HSTS Exam Management System - Exam Builder"
            );
            controller.configure(stage, client, () -> returnToTeacherDashboard(loginResult));
        } catch (IOException | RuntimeException exception) {
            try {
                showTeacherDashboard(loginResult);
                showNavigationError("Unable to open exam management");
            } catch (IOException | RuntimeException restoreException) {
                cleanupAfterNavigationFailure();
            }
        }
    }

    private void showApprovalRequests(LoginResult loginResult) {
        if (loginResult == null || loginResult.getRole() != UserRole.COORDINATOR) {
            showNavigationError("Approval requests are available only to coordinators");
            return;
        }
        try {
            ApprovalRequestsPage controller = SceneNavigator.switchScene(
                    stage,
                    "/hsts/client/boundary/approval-requests-page.fxml",
                    "HSTS Exam Management System - Approval Box"
            );
            controller.configure(stage, client, () -> returnToTeacherDashboard(loginResult));
        } catch (IOException | RuntimeException exception) {
            try {
                showTeacherDashboard(loginResult);
                showNavigationError("Unable to open approval requests");
            } catch (IOException | RuntimeException restoreException) {
                cleanupAfterNavigationFailure();
            }
        }
    }

    private void returnToTeacherDashboard(LoginResult loginResult) {
        try {
            showTeacherDashboard(loginResult);
        } catch (IOException | RuntimeException exception) {
            cleanupAfterNavigationFailure();
        }
    }

    private void logout() {
        if (logoutInProgress) {
            return;
        }

        logoutInProgress = true;
        sendLogoutAndReturnToLogin(null);
    }

    private void cleanupAfterNavigationFailure() {
        if (logoutInProgress) {
            return;
        }

        logoutInProgress = true;
        sendLogoutAndReturnToLogin("Unable to open dashboard");
    }

    private void sendLogoutAndReturnToLogin(String loginError) {
        Client sessionClient = client;
        CompletableFuture
                .supplyAsync(() -> sessionClient.sendRequest(new Request(RequestType.LOGOUT, null)))
                .whenComplete((response, exception) -> {
                    if (exception == null && response != null && response.isSuccess()) {
                        Platform.runLater(() -> finishSuccessfulLogout(loginError));
                    } else {
                        recoverClientAfterLogoutFailure(sessionClient);
                    }
                });
    }

    private void finishSuccessfulLogout(String loginError) {
        currentLogin = null;
        logoutInProgress = false;
        try {
            showLogin(loginError);
        } catch (IOException | RuntimeException exception) {
            failStartup("Unable to open the login page");
        }
    }

    private void recoverClientAfterLogoutFailure(Client failedClient) {
        CompletableFuture
                .supplyAsync(() -> {
                    closeQuietly(failedClient);
                    try {
                        return new Client(serverHost, serverPort);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Unable to reconnect", exception);
                    }
                })
                .whenComplete((replacementClient, exception) -> Platform.runLater(() -> {
                    if (exception != null) {
                        client = null;
                        logoutInProgress = false;
                        failStartup("Unable to reconnect to HSTS server");
                        return;
                    }

                    client = replacementClient;
                    currentLogin = null;
                    logoutInProgress = false;
                    try {
                        showLogin("Unable to log out cleanly. Please sign in again.");
                    } catch (IOException | RuntimeException navigationException) {
                        failStartup("Unable to open the login page");
                    }
                }));
    }

    private void showNavigationError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Navigation Error");
        alert.setHeaderText("Unable to open page");
        alert.setContentText(message);
        alert.show();
    }

    private void failStartup(String message) {
        closeQuietly(client);
        client = null;

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("HSTS Startup Error");
        alert.setHeaderText("The HSTS client could not start");
        alert.setContentText(message == null || message.isBlank()
                ? "Unable to start the HSTS client"
                : message);
        alert.showAndWait();
        Platform.exit();
    }

    private void closeQuietly(Client clientToClose) {
        if (clientToClose == null) {
            return;
        }

        try {
            clientToClose.close();
        } catch (IOException ignored) {
        }
    }
}
