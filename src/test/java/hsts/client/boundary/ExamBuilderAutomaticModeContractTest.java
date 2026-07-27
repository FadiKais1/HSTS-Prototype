package hsts.client.boundary;

import hsts.common.CourseSummaryDTO;
import hsts.common.ExamQuestionDTO;
import hsts.common.GenerateExamPayload;
import hsts.common.type.DifficultyLevel;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExamBuilderAutomaticModeContractTest {
    private static final Path FXML_PATH = Path.of(
            "src/main/resources/hsts/client/boundary/exam-builder-page.fxml"
    );
    private static final Path CONTROLLER_PATH = Path.of(
            "src/main/java/hsts/client/boundary/ExamBuilderPage.java"
    );

    @Test
    public void fxmlProvidesWellFormedManualDefaultAndAutomaticControls() throws Exception {
        Document document = parse(FXML_PATH);
        Map<String, Element> elements = new LinkedHashMap<>();
        collectElementsById(document.getDocumentElement(), elements);

        for (String id : Set.of(
                "creationModeGroup", "manualModeRadio", "automaticModeRadio",
                "automaticCriteriaPane", "topicField", "difficultyComboBox",
                "questionCountField", "generateButton", "manualQuestionPane"
        )) {
            assertTrue("Missing automatic-mode fx:id: " + id, elements.containsKey(id));
        }

        assertTrue(Boolean.parseBoolean(
                elements.get("manualModeRadio").getAttribute("selected")
        ));
        assertFalse(Boolean.parseBoolean(
                elements.get("automaticModeRadio").getAttribute("selected")
        ));
        assertFalse(Boolean.parseBoolean(
                elements.get("automaticCriteriaPane").getAttribute("visible")
        ));
        assertFalse(Boolean.parseBoolean(
                elements.get("automaticCriteriaPane").getAttribute("managed")
        ));
        assertEquals("#handleManualMode",
                elements.get("manualModeRadio").getAttribute("onAction"));
        assertEquals("#handleAutomaticMode",
                elements.get("automaticModeRadio").getAttribute("onAction"));
        assertEquals("#handleGenerateDraft",
                elements.get("generateButton").getAttribute("onAction"));
    }

    @Test
    public void difficultyOptionsUseEveryExistingEnumValueInOrder() {
        assertEquals(
                List.of(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD),
                ExamBuilderPage.automaticDifficulties()
        );
    }

    @Test
    public void automaticPayloadMapsOnlyPresentationCriteriaWithoutIdentity() {
        CourseSummaryDTO course = course();

        GenerateExamPayload payload = ExamBuilderPage.buildAutomaticPayload(
                course,
                "  Generated Midterm  ",
                " 75 ",
                "  Private notes  ",
                "  Read carefully  ",
                "  Calculus  ",
                DifficultyLevel.HARD,
                " 8 "
        );

        assertEquals(17, payload.getCourseId());
        assertEquals("Generated Midterm", payload.getTitle());
        assertEquals(75, payload.getDurationMinutes());
        assertEquals("Private notes", payload.getTeacherNotes());
        assertEquals("Read carefully", payload.getStudentInstructions());
        assertEquals("Calculus", payload.getTopic());
        assertEquals(DifficultyLevel.HARD, payload.getDifficulty());
        assertEquals(8, payload.getQuestionCount());

        Set<String> fields = Arrays.stream(GenerateExamPayload.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "courseId", "title", "durationMinutes", "teacherNotes",
                "studentInstructions", "topic", "difficulty", "questionCount"
        ), fields);
    }

    @Test
    public void automaticPresentationValidationCoversEveryRequiredInput() {
        CourseSummaryDTO course = course();

        assertEquals("Select an assigned course.", validate(null, "Title", "60",
                "Instructions", "Topic", DifficultyLevel.EASY, "3"));
        assertEquals("Exam title is required.", validate(course, " ", "60",
                "Instructions", "Topic", DifficultyLevel.EASY, "3"));
        assertEquals("Duration must be a positive whole number.", validate(course, "Title", "0",
                "Instructions", "Topic", DifficultyLevel.EASY, "3"));
        assertEquals("Student instructions are required.", validate(course, "Title", "60",
                " ", "Topic", DifficultyLevel.EASY, "3"));
        assertEquals("Topic is required.", validate(course, "Title", "60",
                "Instructions", " ", DifficultyLevel.EASY, "3"));
        assertEquals("Difficulty is required.", validate(course, "Title", "60",
                "Instructions", "Topic", null, "3"));
        assertEquals("Question count must be a positive whole number.",
                validate(course, "Title", "60", "Instructions", "Topic",
                        DifficultyLevel.EASY, "not a number"));
        assertNull(validate(course, "Title", "60", "Instructions", "Topic",
                DifficultyLevel.EASY, "3"));
    }

    @Test
    public void generatedExamQuestionsPopulateLoadedEditorInServerOrder() {
        ExamQuestionDTO second = question(12, 4, 2, 66.67);
        ExamQuestionDTO first = question(11, 3, 1, 33.33);

        List<ExamBuilderPage.SelectedQuestionItem> items =
                ExamBuilderPage.selectedItemsFromExam(List.of(second, first));

        assertEquals(2, items.size());
        assertEquals(11, items.get(0).getQuestionId());
        assertEquals(3, items.get(0).getQuestionVersionNo());
        assertEquals(1, items.get(0).getOrderNumber());
        assertEquals(33.33, items.get(0).getScore(), 0);
        assertEquals(12, items.get(1).getQuestionId());
        assertEquals(4, items.get(1).getQuestionVersionNo());
        assertEquals(2, items.get(1).getOrderNumber());
        assertEquals(66.67, items.get(1).getScore(), 0);
    }

    @Test
    public void automaticActionUsesOnlyGenerateAndExistingLoadedExamFlow() throws Exception {
        String source = Files.readString(CONTROLLER_PATH);
        String action = methodSource(source, "private void handleGenerateDraft()");

        assertTrue(action.contains("new GenerateExamPayload")
                || source.contains("static GenerateExamPayload buildAutomaticPayload"));
        assertTrue(action.contains("examClientController.generateExam(payload)"));
        assertTrue(action.contains("Platform.runLater("));
        assertTrue(action.contains("showExam(generated)"));
        assertTrue(action.contains("loadExamSummaries("));
        assertFalse(action.contains("createExam("));
        assertFalse(action.contains("submitExamForApproval("));
        assertFalse(action.contains("approveExam("));
        assertFalse(action.contains("new Client("));
        assertFalse(action.contains("client.close("));

        assertTrue(source.contains("examClientController.createExam(payload)"));
        assertTrue(source.contains("examClientController.updateExam(payload)"));
        assertTrue(source.contains("examClientController.submitExamForApproval(payload)"));
        assertTrue(source.contains("backHandler.run()"));
    }

    private static String validate(CourseSummaryDTO course, String title,
                                   String duration, String instructions, String topic,
                                   DifficultyLevel difficulty, String count) {
        return ExamBuilderPage.validateAutomaticInput(
                course, title, duration, instructions, topic, difficulty, count
        );
    }

    private static CourseSummaryDTO course() {
        return new CourseSummaryDTO(
                17, 4, "MATH-201", "Mathematics", "Mathematics",
                "11", "2026"
        );
    }

    private static ExamQuestionDTO question(int id, int version, int order, double score) {
        return new ExamQuestionDTO(
                id, version, order, score, "Question " + id, "Calculus", "HARD", "",
                "A", "B", "C", "D", 2
        );
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static void collectElementsById(Element element, Map<String, Element> elements) {
        String id = element.getAttribute("fx:id");
        if (!id.isEmpty()) {
            assertFalse("Duplicate fx:id: " + id, elements.containsKey(id));
            elements.put(id, element);
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                collectElementsById(childElement, elements);
            }
        }
    }

    private static String methodSource(String source, String methodStart) {
        int start = source.indexOf(methodStart);
        int openingBrace = source.indexOf('{', start);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Method not found: " + methodStart);
    }
}
