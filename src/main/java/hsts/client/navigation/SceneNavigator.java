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

    private static final String THEME_STYLESHEET =
            "/hsts/client/boundary/hsts-theme.css";

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
            Scene scene = new Scene(root);
            applyTheme(scene);
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root);
            applyTheme(stage.getScene());
        }

        if (title != null) {
            stage.setTitle(title);
        }

        stage.show();
        return loader.getController();
    }

    /**
     * Applies the HSTS base stylesheet.
     *
     * <p>Without it the text colour comes from the operating system base
     * theme, so on a machine using dark mode or a high-contrast theme any
     * label without an explicit colour renders white against the white
     * cards and becomes unreadable.</p>
     */
    private static void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        URL stylesheet = SceneNavigator.class.getResource(THEME_STYLESHEET);
        if (stylesheet == null) {
            return;
        }
        String uri = stylesheet.toExternalForm();
        if (!scene.getStylesheets().contains(uri)) {
            scene.getStylesheets().add(uri);
        }
    }
}
