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
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationsPageContractTest {
    private static final Path FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/notifications-page.fxml"
    );
    private static final Path SOURCE_PATH = Path.of(
            "src/main/java/hsts/client/boundary/NotificationsPage.java"
    );

    @Test
    public void fxmlIsWellFormedAndMatchesControllerFieldsAndActions() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(FXML_PATH.toFile());
        assertEquals("hsts.client.boundary.NotificationsPage",
                document.getDocumentElement().getAttribute("fx:controller"));

        Map<String, String> ids = new HashMap<>();
        Set<String> actions = new HashSet<>();
        collect(document.getDocumentElement(), ids, actions);
        for (String required : Set.of(
                "identityLabel", "statusLabel", "refreshButton", "backButton",
                "notificationTable", "stateColumn", "typeColumn", "titleColumn",
                "createdColumn", "messageArea"
        )) assertTrue("Missing fx:id: " + required, ids.containsKey(required));
        for (String id : ids.keySet()) {
            Field field = NotificationsPage.class.getDeclaredField(id);
            assertTrue(field.isAnnotationPresent(FXML.class));
        }
        assertEquals(Set.of("handleRefresh", "handleBack"), actions);
        for (String action : actions) {
            Method method = NotificationsPage.class.getDeclaredMethod(action);
            assertTrue(method.isAnnotationPresent(FXML.class));
        }
    }

    @Test
    public void inboxUsesExplicitStateAndSharedControllerWithoutClientLifecycle()
            throws Exception {
        String source = Files.readString(SOURCE_PATH);
        String fxml = Files.readString(FXML_PATH);

        assertTrue(source.contains("new NotificationClientController("));
        assertTrue(source.contains("\"READ\" : \"UNREAD\""));
        assertTrue(source.contains("getMyNotifications()"));
        assertTrue(source.contains("markAsRead(notification.getNotificationId())"));
        assertTrue(source.contains("request != generation"));
        assertTrue(fxml.contains("No notifications."));
        assertFalse(source.contains("client.sendRequest"));
        assertFalse(source.contains("new Client("));
        assertFalse(source.contains("client.close("));
        assertFalse(source.contains("hsts.server."));
    }

    @Test
    public void dashboardsAndMainClientMakeInboxReachableWithoutPolling() throws Exception {
        String teacher = Files.readString(Path.of(
                "src/main/java/hsts/client/boundary/TeacherDashboard.java"));
        String student = Files.readString(Path.of(
                "src/main/java/hsts/client/boundary/StudentDashboard.java"));
        String main = Files.readString(Path.of(
                "src/main/java/hsts/client/MainClient.java"));

        assertTrue(teacher.contains("configureNotifications"));
        assertTrue(student.contains("configureNotifications"));
        assertTrue(teacher.contains("getUnreadCount()"));
        assertTrue(student.contains("getUnreadCount()"));
        assertTrue(main.contains("/hsts/client/boundary/notifications-page.fxml"));
        assertTrue(main.contains("showNotifications(loginResult)"));
        assertFalse(teacher.contains("Timeline"));
        assertFalse(student.contains("Timeline"));
    }

    private static void collect(Element element, Map<String, String> ids,
                                Set<String> actions) {
        String id = element.getAttribute("fx:id");
        if (!id.isEmpty()) {
            assertFalse("Duplicate fx:id: " + id, ids.containsKey(id));
            ids.put(id, element.getTagName());
        }
        String action = element.getAttribute("onAction");
        if (action.startsWith("#")) actions.add(action.substring(1));
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                collect(childElement, ids, actions);
            }
        }
    }
}
