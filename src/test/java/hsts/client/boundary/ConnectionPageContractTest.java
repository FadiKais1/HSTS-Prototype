package hsts.client.boundary;

import javafx.fxml.FXML;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * NFR 15 asks for a screen that initialises the connection to the server, so
 * the client can be pointed at a different host or port without editing code or
 * a configuration file.
 *
 * <p>Every other boundary screen has a contract test; this one did not, so the
 * only evidence that the first screen a user meets still worked was manual
 * testing.</p>
 */
public class ConnectionPageContractTest {

    private static final Path FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/connection-page.fxml"
    );
    private static final Path SOURCE_PATH = Path.of(
            "src/main/java/hsts/client/boundary/ConnectionPage.java"
    );
    private static final Path MAIN_CLIENT_PATH = Path.of(
            "src/main/java/hsts/client/MainClient.java"
    );

    @Test
    public void fxmlIsWellFormedAndMatchesControllerFieldsAndActions() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(FXML_PATH.toFile());
        assertEquals("hsts.client.boundary.ConnectionPage",
                document.getDocumentElement().getAttribute("fx:controller"));

        Map<String, String> ids = new HashMap<>();
        Set<String> actions = new HashSet<>();
        collect(document.getDocumentElement(), ids, actions);

        // The host and port are what make the screen worth having: without them
        // the client could only ever reach a server named at build time.
        for (String required : List.of(
                "hostField", "portField", "connectButton", "statusLabel", "errorLabel"
        )) {
            assertTrue("Missing fx:id " + required, ids.containsKey(required));
        }

        for (Map.Entry<String, String> entry : ids.entrySet()) {
            Field field = ConnectionPage.class.getDeclaredField(entry.getKey());
            assertTrue(field.isAnnotationPresent(FXML.class));
        }

        assertEquals(Set.of("handleConnect"), actions);
        for (String action : actions) {
            Method method = ConnectionPage.class.getDeclaredMethod(action);
            assertTrue(method.isAnnotationPresent(FXML.class));
        }
    }

    /**
     * A rejected host or port must be reported on the screen rather than
     * attempted, so a typed mistake does not become a silent failure to connect.
     */
    @Test
    public void inputIsValidatedBeforeAnyConnectionIsAttempted() throws Exception {
        String source = Files.readString(SOURCE_PATH);

        int blankHost = source.indexOf("host.isBlank()");
        int portRange = source.indexOf("port < 1 || port > 65535");
        int connect = source.indexOf("new Client(");

        assertTrue("The host must be checked before connecting",
                blankHost >= 0 && blankHost < connect);
        assertTrue("The port range must be checked before connecting",
                portRange >= 0 && portRange < connect);

        // A non-numeric port is a typing mistake, not a crash.
        assertTrue(source.contains("catch (NumberFormatException"));

        // Failures belong on the screen, not only in the console.
        assertTrue(source.contains("showError("));
        assertFalse(source.contains("printStackTrace"));
    }

    /** The connection screen is the first thing a user sees. */
    @Test
    public void theClientOpensOnTheConnectionScreen() throws Exception {
        String source = Files.readString(MAIN_CLIENT_PATH);

        int connectionScene = source.indexOf("connection-page.fxml");
        int loginScene = source.indexOf("login-page.fxml");

        assertTrue("The client must load the connection screen", connectionScene >= 0);
        assertTrue(
                "The connection screen must come before the login screen",
                loginScene < 0 || connectionScene < loginScene
        );
    }

    private static void collect(Node node, Map<String, String> ids, Set<String> actions) {
        if (node instanceof Element element) {
            String id = element.getAttribute("fx:id");
            if (!id.isBlank()) {
                assertFalse("Duplicate fx:id " + id, ids.containsKey(id));
                ids.put(id, element.getTagName());
            }
            String action = element.getAttribute("onAction");
            if (!action.isBlank()) {
                actions.add(action.replace("#", ""));
            }
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            collect(children.item(index), ids, actions);
        }
    }
}
