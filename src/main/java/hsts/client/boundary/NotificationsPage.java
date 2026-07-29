package hsts.client.boundary;

import hsts.client.control.NotificationClientController;
import hsts.client.net.Client;
import hsts.common.LoginResult;
import hsts.common.NotificationDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class NotificationsPage {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObservableList<NotificationDTO> notifications =
            FXCollections.observableArrayList();
    private Stage stage;
    private Runnable backHandler;
    private NotificationClientController controller;
    private boolean disposed;
    private boolean loading;
    private long generation;

    @FXML private Label identityLabel;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private TableView<NotificationDTO> notificationTable;
    @FXML private TableColumn<NotificationDTO, String> stateColumn;
    @FXML private TableColumn<NotificationDTO, String> typeColumn;
    @FXML private TableColumn<NotificationDTO, String> titleColumn;
    @FXML private TableColumn<NotificationDTO, String> createdColumn;
    @FXML private TextArea messageArea;

    @FXML
    private void initialize() {
        notificationTable.setItems(notifications);
        notificationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        stateColumn.setCellValueFactory(value -> new SimpleStringProperty(
                value.getValue().isRead() ? "READ" : "UNREAD"
        ));
        typeColumn.setCellValueFactory(value -> new SimpleStringProperty(
                value.getValue().getType().name()
        ));
        titleColumn.setCellValueFactory(value -> new SimpleStringProperty(
                value.getValue().getTitle()
        ));
        createdColumn.setCellValueFactory(value -> new SimpleStringProperty(
                TIME_FORMAT.format(value.getValue().getCreatedAt())
        ));
        notificationTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> openNotification(selected)
        );
        messageArea.setText("Select a notification to read it.");
    }

    public void configure(Stage stage, Client client, LoginResult loginResult,
                          Runnable backHandler) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.controller = new NotificationClientController(
                Objects.requireNonNull(client, "client")
        );
        this.backHandler = Objects.requireNonNull(backHandler, "backHandler");
        Objects.requireNonNull(loginResult, "loginResult");
        identityLabel.setText(loginResult.getFullName() + " · "
                + loginResult.getRole().name());
        disposed = false;
        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleBack() {
        if (disposed || backHandler == null) return;
        disposed = true;
        generation++;
        backHandler.run();
    }

    private void refresh() {
        if (disposed || loading || controller == null) return;
        long request = ++generation;
        loading = true;
        statusLabel.setText("Loading notifications...");
        refreshButton.setDisable(true);
        controller.getMyNotifications().whenComplete((loaded, failure) ->
                Platform.runLater(() -> {
                    if (disposed || request != generation) return;
                    loading = false;
                    refreshButton.setDisable(false);
                    if (failure != null) {
                        statusLabel.setText(cleanError(failure));
                    } else {
                        notifications.setAll(loaded);
                        messageArea.setText("Select a notification to read it.");
                        statusLabel.setText(loaded.isEmpty()
                                ? "No notifications."
                                : "Notifications loaded newest first.");
                    }
                }));
    }

    private void openNotification(NotificationDTO notification) {
        if (notification == null || disposed) {
            messageArea.setText("Select a notification to read it.");
            return;
        }
        messageArea.setText(notification.getMessage());
        if (notification.isRead()) return;
        long request = generation;
        controller.markAsRead(notification.getNotificationId())
                .whenComplete((updated, failure) -> Platform.runLater(() -> {
                    if (disposed || request != generation) return;
                    if (failure != null) {
                        statusLabel.setText(cleanError(failure));
                        return;
                    }
                    int index = notifications.indexOf(notification);
                    if (index >= 0) notifications.set(index, updated);
                }));
    }

    private static String cleanError(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? "Unable to load notifications" : current.getMessage();
    }
}
