package hsts.client.boundary;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrincipalOversightPageContractTest {
    private static final Path FXML = Path.of(
            "src/main/resources/hsts/client/boundary/principal-oversight-page.fxml"
    );
    private static final Path SOURCE = Path.of(
            "src/main/java/hsts/client/boundary/PrincipalOversightPage.java"
    );

    @Test
    public void realFxmlLoadsAndLaysOutInFreshJavaFxProcess() throws Exception {
        String javaExecutable = Path.of(
                System.getProperty("java.home"), "bin", "java.exe"
        ).toString();
        Process process = new ProcessBuilder(
                javaExecutable,
                "-cp",
                System.getProperty("java.class.path"),
                LoadingHarness.class.getName()
        ).redirectErrorStream(true).start();

        assertTrue("JavaFX loading harness timed out",
                process.waitFor(30, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(output, 0, process.exitValue());
    }

    @Test
    public void fxmlIsWellFormedAndContainsThreeReadOnlySections() throws Exception {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(FXML.toFile());
        String fxml = Files.readString(FXML);
        assertTrue(fxml.contains("fx:controller=\"hsts.client.boundary.PrincipalOversightPage\""));
        assertTrue(fxml.contains("<Tab text=\"Questions\">"));
        assertTrue(fxml.contains("<Tab text=\"Exams\">"));
        assertTrue(fxml.contains("<Tab text=\"Results\">"));
        assertTrue(fxml.contains("editable=\"false\""));
        for (String mutation : new String[]{"Create", "Approve", "Reject", "Publish",
                "Deactivate", "Delete", "Schedule", "Extend"}) {
            assertFalse(fxml.contains("text=\"" + mutation));
        }
    }

    @Test
    public void controllerUsesSharedAsyncClientAndHasStaleResponseGuards() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("new PrincipalOversightClientController(client)"));
        assertTrue(source.contains("whenComplete"));
        assertTrue(source.contains("Platform.runLater"));
        assertTrue(source.contains("disposed"));
        assertTrue(source.contains("questionGeneration"));
        assertTrue(source.contains("submissionGeneration"));
        assertTrue(source.contains("if (refreshing) return;"));
        assertTrue(source.contains("refreshButton.setDisable(true)"));
        assertTrue(source.contains("CompletableFuture.allOf("));
        assertTrue(source.contains("backButton.setDisable(true)"));
        assertFalse(source.contains("new Client("));
        assertFalse(source.contains(".join()"));
        assertFalse(source.contains(".get()"));
        assertFalse(source.contains("sendRequest("));
    }

    public static final class LoadingHarness {
        private LoadingHarness() {
        }

        public static void main(String[] args) {
            Platform.startup(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            PrincipalOversightPageContractTest.class.getResource(
                                    "/hsts/client/boundary/principal-oversight-page.fxml"
                            )
                    );
                    Parent root = loader.load();
                    new Scene(root, 1280, 800);
                    root.applyCss();
                    root.layout();
                    if (!(loader.getController() instanceof PrincipalOversightPage)) {
                        throw new AssertionError("Unexpected FXML controller");
                    }
                    System.exit(0);
                } catch (Throwable failure) {
                    failure.printStackTrace(System.err);
                    System.exit(1);
                }
            });
        }
    }
}
