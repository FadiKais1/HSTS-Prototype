package hsts.client.boundary;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QuestionBankPageContractTest {
    private static final Path FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/question-bank-page.fxml"
    );
    private static final Path CONTROLLER_PATH = Path.of(
            "src/main/java/hsts/client/boundary/QuestionBankPageController.java"
    );

    @Test
    public void fxmlIsWellFormedAndUsesExpectedController() throws Exception {
        Document document = parseFxml();

        assertEquals(
                "hsts.client.boundary.QuestionBankPageController",
                document.getDocumentElement().getAttribute("fx:controller")
        );
    }

    @Test
    public void everyFxIdIsUniqueAndHasCompatibleControllerField() throws Exception {
        Document document = parseFxml();
        Map<String, String> idsAndElementTypes = new LinkedHashMap<>();
        collectIds(document.getDocumentElement(), idsAndElementTypes);

        for (Map.Entry<String, String> entry : idsAndElementTypes.entrySet()) {
            Field field = QuestionBankPageController.class.getDeclaredField(entry.getKey());
            assertEquals(entry.getValue(), field.getType().getSimpleName());
        }
    }

    @Test
    public void everyFxmlActionHasControllerMethod() throws Exception {
        Document document = parseFxml();
        Set<String> actions = new HashSet<>();
        collectActions(document.getDocumentElement(), actions);
        Set<String> methodNames = new HashSet<>();
        for (Method method : QuestionBankPageController.class.getDeclaredMethods()) {
            methodNames.add(method.getName());
        }

        for (String action : actions) {
            assertTrue("Missing controller action: " + action, methodNames.contains(action));
        }
    }

    @Test
    public void requiredControlsAndActionsArePresent() throws Exception {
        Document document = parseFxml();
        Map<String, String> ids = new LinkedHashMap<>();
        collectIds(document.getDocumentElement(), ids);
        Set<String> actions = new HashSet<>();
        collectActions(document.getDocumentElement(), actions);

        for (String id : Set.of(
                "courseFilterComboBox", "subjectFilterComboBox", "topicFilterField",
                "difficultyFilterComboBox", "statusFilterComboBox", "tableView",
                "contentColumn", "courseColumn", "subjectColumn", "versionColumn",
                "createButton", "updateButton", "statusActionButton", "historyButton",
                "statusLabel"
        )) {
            assertTrue("Missing required fx:id: " + id, ids.containsKey(id));
        }
        for (String action : Set.of(
                "handleApplyFilters", "handleClearFilters", "handleCreateQuestion",
                "handleUpdateQuestion", "handleToggleStatus", "handleShowHistory",
                "handleBack"
        )) {
            assertTrue("Missing required action: " + action, actions.contains(action));
        }
    }

    @Test
    public void controllerUsesOnlyNormalizedQuestionBankReadAndActionApis() throws Exception {
        String source = Files.readString(CONTROLLER_PATH);

        assertFalse(source.contains(".getAllQuestions("));
        assertTrue(source.contains(".getMyCourses("));
        assertTrue(source.contains(".listQuestions("));
        assertTrue(source.contains(".createQuestion("));
        assertTrue(source.contains(".activateQuestion("));
        assertTrue(source.contains(".deactivateQuestion("));
        assertTrue(source.contains(".getQuestionHistory("));
        assertTrue(source.contains("questionClientController.updateQuestion(payload)"));
        assertFalse(source.contains("client.sendRequest("));
        assertFalse(source.contains("authenticatedUserId"));
        assertFalse(source.contains("setUserId("));
    }

    @Test
    public void versionedUpdateUsesSelectedCurrentVersion() throws Exception {
        String source = Files.readString(CONTROLLER_PATH);
        String constructorArguments = findInvocationArguments(
                source,
                "new UpdateQuestionPayload("
        );

        assertNotNull(constructorArguments);
        assertTrue(constructorArguments.contains("selectedQuestion.getVersionNo()"));
        assertFalse(constructorArguments.contains("new UpdateQuestionPayload(questionId, content)"));
    }

    private static Document parseFxml() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(FXML_PATH.toFile());
    }

    private static void collectIds(Element element, Map<String, String> ids) {
        String id = element.getAttribute("fx:id");
        if (!id.isEmpty()) {
            assertFalse("Duplicate fx:id: " + id, ids.containsKey(id));
            ids.put(id, element.getTagName());
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                collectIds(childElement, ids);
            }
        }
    }

    private static void collectActions(Element element, Set<String> actions) {
        String action = element.getAttribute("onAction");
        if (action.startsWith("#")) {
            actions.add(action.substring(1));
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                collectActions(childElement, actions);
            }
        }
    }

    private static String findInvocationArguments(String source, String invocationStart) {
        int start = source.indexOf(invocationStart);
        if (start < 0) {
            return null;
        }
        int openParenthesis = start + invocationStart.length() - 1;
        int depth = 0;
        for (int index = openParenthesis; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return source.substring(openParenthesis + 1, index);
                }
            }
        }
        return null;
    }
}
