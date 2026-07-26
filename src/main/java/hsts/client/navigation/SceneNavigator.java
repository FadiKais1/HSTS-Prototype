package hsts.client.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

// COMPATIBILITY-ONLY: Centralizes JavaFX navigation for Assignment 3 without changing the Assignment 2 UML.
public final class SceneNavigator {
    private SceneNavigator() {
    }

    public static <T> T switchScene(
            Stage stage,
            String fxmlPath,
            String title
    ) throws IOException {
        Objects.requireNonNull(stage);
        Objects.requireNonNull(fxmlPath);

        URL resource = SceneNavigator.class.getResource(fxmlPath);
        if (resource == null) {
            throw new IOException("FXML resource not found: " + fxmlPath);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        Parent root = loader.load();

        if (stage.getScene() == null) {
            stage.setScene(new Scene(root));
        } else {
            stage.getScene().setRoot(root);
        }

        if (title != null) {
            stage.setTitle(title);
        }

        stage.show();
        return loader.getController();
    }
}
