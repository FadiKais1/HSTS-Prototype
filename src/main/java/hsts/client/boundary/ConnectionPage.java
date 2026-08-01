package hsts.client.boundary;

import hsts.client.net.Client;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Connection initialization boundary.
 *
 * <p>Satisfies the non-functional requirement that the client provides a GUI for
 * initializing the client/server connection, so the operator can point the client
 * at any server host and port without editing environment variables.</p>
 */
public class ConnectionPage {
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private Label statusLabel;
    @FXML private Label errorLabel;
    @FXML private Button connectButton;

    private ConnectionHandler connectionHandler;

    /** Invoked once a live connection to the server has been established. */
    @FunctionalInterface
    public interface ConnectionHandler {
        void onConnected(Client client, String host, int port);
    }

    /**
     * Prepares the page with the suggested defaults and the callback to invoke
     * once a connection has been established.
     */
    public void configure(String defaultHost, int defaultPort, ConnectionHandler handler) {
        this.connectionHandler = handler;
        hostField.setText(defaultHost == null ? "localhost" : defaultHost);
        portField.setText(String.valueOf(defaultPort));
        hideMessages();
        hostField.requestFocus();
    }

    /** Displays a connection error raised elsewhere, for example after a dropped session. */
    public void showConnectionError(String message) {
        showError(message);
    }

    @FXML
    private void handleConnect() {
        hideMessages();

        String host = hostField.getText() == null ? "" : hostField.getText().trim();
        if (host.isBlank()) {
            showError("Server host must not be empty");
            hostField.requestFocus();
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portField.getText() == null ? "" : portField.getText().trim());
        } catch (NumberFormatException exception) {
            showError("Server port must be a number from 1 to 65535");
            portField.requestFocus();
            return;
        }

        if (port < 1 || port > 65535) {
            showError("Server port must be between 1 and 65535");
            portField.requestFocus();
            return;
        }

        setBusy(true, "Connecting to " + host + ":" + port + "...");

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return new Client(host, port);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception.getMessage(), exception);
                    }
                })
                .whenComplete((client, exception) -> Platform.runLater(() -> {
                    if (exception != null || client == null) {
                        setBusy(false, null);
                        showError("Unable to connect to the HSTS server at "
                                + host + ":" + port
                                + ". Check that the server is running and reachable.");
                        return;
                    }

                    setBusy(false, null);
                    if (connectionHandler != null) {
                        connectionHandler.onConnected(client, host, port);
                    }
                }));
    }

    private void setBusy(boolean busy, String message) {
        connectButton.setDisable(busy);
        hostField.setDisable(busy);
        portField.setDisable(busy);

        if (busy && message != null) {
            statusLabel.setText(message);
            statusLabel.setManaged(true);
            statusLabel.setVisible(true);
        } else {
            statusLabel.setText("");
            statusLabel.setManaged(false);
            statusLabel.setVisible(false);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "Unable to connect" : message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void hideMessages() {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        statusLabel.setText("");
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);
    }
}