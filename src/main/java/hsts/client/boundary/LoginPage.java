package hsts.client.boundary;

import hsts.client.net.Client;
import hsts.common.LoginRequestPayload;
import hsts.common.LoginResult;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.server.entity.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LoginPage {
    private Client client;
    private String emailInput;
    private String passwordInput;
    private String errorMessage;

    // COMPATIBILITY-ONLY: JavaFX state for the Assignment 3 login boundary.
    private Stage stage;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button loginButton;
    private Consumer<LoginResult> loginSuccessHandler;

    public void configure(Stage stage, Client client, Consumer<LoginResult> loginSuccessHandler) {
        this.stage = stage;
        this.client = client;
        this.loginSuccessHandler = loginSuccessHandler;
    }

    @FXML
    private void initialize() {
        showLoginError("");
    }

    @FXML
    private void handleLogin() {
        submitLogin(emailField.getText(), passwordField.getText());
    }

    public void submitLogin(String email, String password) {
        emailInput = email;
        passwordInput = password;
        showLoginError("");

        if (stage == null || client == null || loginSuccessHandler == null) {
            showLoginError("Login page is not configured");
            setLoginDisabled(false);
            return;
        }

        setLoginDisabled(true);
        Client configuredClient = client;
        Request request = new Request(
                RequestType.LOGIN,
                new LoginRequestPayload(email, password)
        );

        CompletableFuture
                .supplyAsync(() -> configuredClient.sendRequest(request))
                .whenComplete((response, exception) -> Platform.runLater(
                        () -> processLoginResult(response, exception)
                ));
    }

    public void showLoginError(String message) {
        errorMessage = message;
        if (errorLabel != null) {
            String visibleMessage = message == null ? "" : message;
            boolean hasMessage = !visibleMessage.isBlank();
            errorLabel.setText(visibleMessage);
            errorLabel.setManaged(hasMessage);
            errorLabel.setVisible(hasMessage);
        }
    }

    public void redirectUser(User user) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    // COMPATIBILITY-ONLY: LoginResult deliberately excludes password-bearing User state.
    public void redirectUser(LoginResult loginResult) {
        if (stage == null || client == null || loginSuccessHandler == null) {
            showLoginError("Login page is not configured");
            return;
        }

        loginSuccessHandler.accept(loginResult);
    }

    public void clearFields() {
        emailInput = "";
        passwordInput = "";
        if (emailField != null) {
            emailField.clear();
        }
        if (passwordField != null) {
            passwordField.clear();
        }
    }

    private void processLoginResult(Response response, Throwable exception) {
        setLoginDisabled(false);

        if (exception != null) {
            showLoginError("Unable to complete login");
            return;
        }
        if (response == null) {
            showLoginError("No response received from server");
            return;
        }
        if (!response.isSuccess()) {
            String message = response.getMessage();
            showLoginError(message == null || message.isBlank() ? "Login failed" : message);
            return;
        }
        if (!(response.getPayload() instanceof LoginResult loginResult)) {
            showLoginError("Invalid login response from server");
            return;
        }

        passwordInput = "";
        if (passwordField != null) {
            passwordField.clear();
        }

        try {
            redirectUser(loginResult);
        } catch (RuntimeException redirectException) {
            showLoginError("Unable to continue after login");
        }
    }

    private void setLoginDisabled(boolean disabled) {
        if (loginButton != null) {
            loginButton.setDisable(disabled);
        }
    }
}
