package hsts.common;

import hsts.common.type.ExamStatus;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamContractTest {
    private static final Set<String> FORBIDDEN_PAYLOAD_FIELDS = Set.of(
            "userId", "teacherId", "coordinatorId", "createdByUserId",
            "updatedByUserId", "reviewedByUserId", "approvedByUserId", "role",
            "status", "examCode", "totalScore", "createdAt", "submittedAt",
            "reviewedAt", "approvedAt"
    );

    @Test
    public void selectionPayloadMapsAndSerializesEveryField() throws Exception {
        ExamQuestionSelectionPayload restored = roundTrip(selection(31, 4, 2, 37.5));

        assertEquals(31, restored.getQuestionId());
        assertEquals(4, restored.getQuestionVersionNo());
        assertEquals(2, restored.getOrderNumber());
        assertEquals(37.5, restored.getScore(), 0.0);
    }

    @Test
    public void createPayloadMapsSerializesAndProtectsQuestionOrder() throws Exception {
        ExamQuestionSelectionPayload first = selection(10, 2, 1, 40.0);
        ExamQuestionSelectionPayload second = selection(20, 3, 2, 60.0);
        List<ExamQuestionSelectionPayload> original = new ArrayList<>(List.of(first, second));
        CreateExamPayload payload = new CreateExamPayload(
                7, "Midterm", 90, "Teacher only", "Read carefully", original
        );

        original.clear();
        assertEquals(List.of(first, second), payload.getQuestions());
        assertThrows(UnsupportedOperationException.class,
                () -> payload.getQuestions().add(selection(30, 1, 3, 1.0)));

        CreateExamPayload restored = roundTrip(payload);
        assertEquals(7, restored.getCourseId());
        assertEquals("Midterm", restored.getTitle());
        assertEquals(90, restored.getDurationMinutes());
        assertEquals("Teacher only", restored.getTeacherNotes());
        assertEquals("Read carefully", restored.getStudentInstructions());
        assertSelection(restored.getQuestions().get(0), 10, 2, 1, 40.0);
        assertSelection(restored.getQuestions().get(1), 20, 3, 2, 60.0);
    }

    @Test
    public void updatePayloadMapsSerializesAndProtectsQuestionOrder() throws Exception {
        ExamQuestionSelectionPayload first = selection(40, 5, 1, 25.0);
        ExamQuestionSelectionPayload second = selection(50, 8, 2, 75.0);
        List<ExamQuestionSelectionPayload> original = new ArrayList<>(List.of(first, second));
        UpdateExamPayload payload = new UpdateExamPayload(
                91, 6, "Revised", 105, "Revised notes",
                "Revised instructions", original
        );

        original.remove(0);
        assertEquals(List.of(first, second), payload.getQuestions());
        assertThrows(UnsupportedOperationException.class,
                () -> payload.getQuestions().remove(0));

        UpdateExamPayload restored = roundTrip(payload);
        assertEquals(91, restored.getExamId());
        assertEquals(6, restored.getExpectedVersionNo());
        assertEquals("Revised", restored.getTitle());
        assertEquals(105, restored.getDurationMinutes());
        assertEquals("Revised notes", restored.getTeacherNotes());
        assertEquals("Revised instructions", restored.getStudentInstructions());
        assertSelection(restored.getQuestions().get(0), 40, 5, 1, 25.0);
        assertSelection(restored.getQuestions().get(1), 50, 8, 2, 75.0);
    }

    @Test
    public void versionAndRejectPayloadsMapAndSerializeWithoutChangingReason() throws Exception {
        ExamVersionPayload version = roundTrip(new ExamVersionPayload(44, 3));
        assertEquals(44, version.getExamId());
        assertEquals(3, version.getExpectedVersionNo());

        String unchangedReason = "  Needs clearer instructions.  ";
        RejectExamPayload rejection = roundTrip(
                new RejectExamPayload(44, 3, unchangedReason)
        );
        assertEquals(44, rejection.getExamId());
        assertEquals(3, rejection.getExpectedVersionNo());
        assertEquals(unchangedReason, rejection.getReason());
    }

    @Test
    public void examQuestionDtoMapsAndSerializesCompleteSnapshot() throws Exception {
        ExamQuestionDTO restored = roundTrip(questionDto(17, 9, 3, 30.5));

        assertEquals(17, restored.getQuestionId());
        assertEquals(9, restored.getQuestionVersionNo());
        assertEquals(3, restored.getOrderNumber());
        assertEquals(30.5, restored.getScore(), 0.0);
        assertEquals("Question 17", restored.getContent());
        assertEquals("Algebra", restored.getTopic());
        assertEquals("HARD", restored.getDifficulty());
        assertEquals("images/17.png", restored.getIllustrationPath());
        assertEquals("One", restored.getAnswerOption1());
        assertEquals("Two", restored.getAnswerOption2());
        assertEquals("Three", restored.getAnswerOption3());
        assertEquals("Four", restored.getAnswerOption4());
        assertEquals(3, restored.getCorrectOptionNumber());
    }

    @Test
    public void examDtoMapsSerializesNullableWorkflowFieldsAndProtectsQuestionOrder()
            throws Exception {
        ExamQuestionDTO first = questionDto(17, 9, 1, 35.0);
        ExamQuestionDTO second = questionDto(18, 2, 2, 65.0);
        List<ExamQuestionDTO> original = new ArrayList<>(List.of(first, second));
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 10, 15);
        ExamDTO dto = new ExamDTO(
                81, "012345", 7, "Mathematics", 2, "Math",
                1002, "Development Teacher", 4, "Midterm", 90,
                "Teacher notes", "Student instructions", 100.0,
                ExamStatus.PENDING_APPROVAL, createdAt, null, null, null,
                null, null, original
        );

        original.clear();
        assertEquals(List.of(first, second), dto.getQuestions());
        assertThrows(UnsupportedOperationException.class,
                () -> dto.getQuestions().clear());

        ExamDTO restored = roundTrip(dto);
        assertEquals(81, restored.getExamId());
        assertEquals("012345", restored.getExamCode());
        assertTrue(restored.getExamCode() instanceof String);
        assertEquals(7, restored.getCourseId());
        assertEquals("Mathematics", restored.getCourseName());
        assertEquals(2, restored.getSubjectId());
        assertEquals("Math", restored.getSubjectName());
        assertEquals(1002, restored.getCreatedByUserId());
        assertEquals("Development Teacher", restored.getCreatorName());
        assertEquals(4, restored.getVersionNo());
        assertEquals("Midterm", restored.getTitle());
        assertEquals(90, restored.getDurationMinutes());
        assertEquals("Teacher notes", restored.getTeacherNotes());
        assertEquals("Student instructions", restored.getStudentInstructions());
        assertEquals(100.0, restored.getTotalScore(), 0.0);
        assertEquals(ExamStatus.PENDING_APPROVAL, restored.getStatus());
        assertEquals(createdAt, restored.getCreatedAt());
        assertNull(restored.getSubmittedAt());
        assertNull(restored.getReviewedByUserId());
        assertNull(restored.getReviewerName());
        assertNull(restored.getReviewedAt());
        assertNull(restored.getRejectionReason());
        assertEquals(17, restored.getQuestions().get(0).getQuestionId());
        assertEquals(18, restored.getQuestions().get(1).getQuestionId());
    }

    @Test
    public void examSummaryMapsSerializesAndPreservesNullableWorkflowFields()
            throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 11, 0);
        ExamSummaryDTO restored = roundTrip(new ExamSummaryDTO(
                82, "A1B2C3", 8, "Physics", 3, "Science", 1003,
                "Development Coordinator", 2, "Final", 120, 100.0,
                ExamStatus.DRAFT, createdAt, null, null, null
        ));

        assertEquals(82, restored.getExamId());
        assertEquals("A1B2C3", restored.getExamCode());
        assertTrue(restored.getExamCode() instanceof String);
        assertEquals(8, restored.getCourseId());
        assertEquals("Physics", restored.getCourseName());
        assertEquals(3, restored.getSubjectId());
        assertEquals("Science", restored.getSubjectName());
        assertEquals(1003, restored.getCreatedByUserId());
        assertEquals("Development Coordinator", restored.getCreatorName());
        assertEquals(2, restored.getVersionNo());
        assertEquals("Final", restored.getTitle());
        assertEquals(120, restored.getDurationMinutes());
        assertEquals(100.0, restored.getTotalScore(), 0.0);
        assertEquals(ExamStatus.DRAFT, restored.getStatus());
        assertEquals(createdAt, restored.getCreatedAt());
        assertNull(restored.getSubmittedAt());
        assertNull(restored.getReviewedAt());
        assertNull(restored.getRejectionReason());
    }

    @Test
    public void commandPayloadsContainNoForbiddenFields() {
        for (Class<?> payloadType : List.of(
                ExamQuestionSelectionPayload.class,
                CreateExamPayload.class,
                UpdateExamPayload.class,
                ExamVersionPayload.class,
                RejectExamPayload.class
        )) {
            Set<String> fields = productionFieldNames(payloadType);
            assertTrue(payloadType.getSimpleName() + " has forbidden fields",
                    fields.stream().noneMatch(FORBIDDEN_PAYLOAD_FIELDS::contains));
        }
    }

    @Test
    public void updatePayloadHasNoCourseAndVersionPayloadHasOnlyApprovedFields() {
        assertFalse(productionFieldNames(UpdateExamPayload.class).contains("courseId"));
        assertEquals(
                Set.of("examId", "expectedVersionNo"),
                productionFieldNames(ExamVersionPayload.class)
        );
    }

    @Test
    public void dtoClassesExposeNoSetters() {
        for (Class<?> dtoType : List.of(
                ExamQuestionDTO.class,
                ExamDTO.class,
                ExamSummaryDTO.class
        )) {
            assertFalse(
                    dtoType.getSimpleName() + " exposes a setter",
                    Arrays.stream(dtoType.getMethods())
                            .map(Method::getName)
                            .anyMatch(name -> name.startsWith("set"))
            );
        }
    }

    private static ExamQuestionSelectionPayload selection(int questionId,
                                                           int questionVersionNo,
                                                           int orderNumber,
                                                           double score) {
        return new ExamQuestionSelectionPayload(
                questionId, questionVersionNo, orderNumber, score
        );
    }

    private static ExamQuestionDTO questionDto(int questionId, int questionVersionNo,
                                                int orderNumber, double score) {
        return new ExamQuestionDTO(
                questionId, questionVersionNo, orderNumber, score,
                "Question " + questionId, "Algebra", "HARD",
                "images/" + questionId + ".png", "One", "Two", "Three",
                "Four", 3
        );
    }

    private static void assertSelection(ExamQuestionSelectionPayload selection,
                                        int questionId, int versionNo,
                                        int orderNumber, double score) {
        assertEquals(questionId, selection.getQuestionId());
        assertEquals(versionNo, selection.getQuestionVersionNo());
        assertEquals(orderNumber, selection.getOrderNumber());
        assertEquals(score, selection.getScore(), 0.0);
    }

    private static Set<String> productionFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !(Modifier.isStatic(field.getModifiers())
                        && field.getName().equals("serialVersionUID")))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
