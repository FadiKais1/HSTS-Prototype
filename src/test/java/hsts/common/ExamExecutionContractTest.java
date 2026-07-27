package hsts.common;

import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;
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

public class ExamExecutionContractTest {
    private static final Set<String> FORBIDDEN_PAYLOAD_IDENTITY_FIELDS = Set.of(
            "userId", "studentId", "teacherId", "coordinatorId",
            "createdByUserId", "reviewedByUserId", "publishedByUserId", "role"
    );

    @Test
    public void schedulingAndCodePayloadsMapAndSerializeWithoutNormalization()
            throws Exception {
        LocalDateTime opening = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime closing = LocalDateTime.of(2026, 8, 10, 11, 0);
        ScheduleExamExecutionPayload schedule = roundTrip(
                new ScheduleExamExecutionPayload(31, 4, opening, closing)
        );

        assertEquals(31, schedule.getExamId());
        assertEquals(4, schedule.getExamVersionNo());
        assertEquals(opening, schedule.getOpeningTime());
        assertEquals(closing, schedule.getClosingTime());

        String unchangedCode = " a9Z ";
        ExecutionCodePayload code = roundTrip(new ExecutionCodePayload(unchangedCode));
        assertEquals(unchangedCode, code.getExecutionCode());
    }

    @Test
    public void attemptCommandPayloadsMapAndSerializeEveryField() throws Exception {
        StartExamPayload start = roundTrip(new StartExamPayload(71, " 123456789 "));
        assertEquals(71, start.getExecutionId());
        assertEquals(" 123456789 ", start.getIdentityConfirmation());

        SaveExamAnswerPayload answer = roundTrip(
                new SaveExamAnswerPayload(81, 91, 3)
        );
        assertEquals(81, answer.getSubmissionId());
        assertEquals(91, answer.getQuestionId());
        assertEquals(3, answer.getSelectedOptionNumber());

        SubmissionIdPayload submission = roundTrip(new SubmissionIdPayload(81));
        assertEquals(81, submission.getSubmissionId());

        ExtendSubmissionTimePayload extension = roundTrip(
                new ExtendSubmissionTimePayload(81, 15, "Approved accommodation")
        );
        assertEquals(81, extension.getSubmissionId());
        assertEquals(15, extension.getExtraMinutes());
        assertEquals("Approved accommodation", extension.getReason());
    }

    @Test
    public void executionSummaryMapsSerializesDatesAndNullableCounts() throws Exception {
        LocalDateTime opening = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime closing = LocalDateTime.of(2026, 8, 10, 11, 0);
        LocalDateTime created = LocalDateTime.of(2026, 8, 1, 12, 30);
        ExamExecutionSummaryDTO summary = roundTrip(new ExamExecutionSummaryDTO(
                71, "A7Z9", 31, 4, "EX1234", "Algebra Midterm",
                12, "Mathematics", opening, closing, 90,
                ExecutionStatus.SCHEDULED, 1002, "Development Teacher",
                created, null, 7, null
        ));

        assertEquals(71, summary.getExecutionId());
        assertEquals("A7Z9", summary.getExecutionCode());
        assertEquals(31, summary.getExamId());
        assertEquals(4, summary.getExamVersionNo());
        assertEquals("EX1234", summary.getExamCode());
        assertEquals("Algebra Midterm", summary.getExamTitle());
        assertEquals(12, summary.getCourseId());
        assertEquals("Mathematics", summary.getCourseName());
        assertEquals(opening, summary.getOpeningTime());
        assertEquals(closing, summary.getClosingTime());
        assertEquals(90, summary.getDurationMinutes());
        assertEquals(ExecutionStatus.SCHEDULED, summary.getStatus());
        assertEquals(1002, summary.getCreatedByUserId());
        assertEquals("Development Teacher", summary.getCreatorName());
        assertEquals(created, summary.getCreatedAt());
        assertNull(summary.getStartedCount());
        assertEquals(Integer.valueOf(7), summary.getSubmittedCount());
        assertNull(summary.getAutoSubmittedCount());
    }

    @Test
    public void executionPreviewMapsAndSerializesOnlySafeMetadata() throws Exception {
        LocalDateTime opening = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime closing = LocalDateTime.of(2026, 8, 10, 11, 0);
        ExamExecutionPreviewDTO preview = roundTrip(new ExamExecutionPreviewDTO(
                71, "A7Z9", 31, 4, "Algebra Midterm", 12,
                "Mathematics", opening, closing, 90, ExecutionStatus.OPEN, true
        ));

        assertEquals(71, preview.getExecutionId());
        assertEquals("A7Z9", preview.getExecutionCode());
        assertEquals(31, preview.getExamId());
        assertEquals(4, preview.getExamVersionNo());
        assertEquals("Algebra Midterm", preview.getExamTitle());
        assertEquals(12, preview.getCourseId());
        assertEquals("Mathematics", preview.getCourseName());
        assertEquals(opening, preview.getOpeningTime());
        assertEquals(closing, preview.getClosingTime());
        assertEquals(90, preview.getDurationMinutes());
        assertEquals(ExecutionStatus.OPEN, preview.getStatus());
        assertTrue(preview.isResumable());
    }

    @Test
    public void studentQuestionAndAnswerMapAndSerializeSafeSnapshots() throws Exception {
        StudentExamQuestionDTO question = roundTrip(question(91, 2));
        assertEquals(91, question.getQuestionId());
        assertEquals(2, question.getQuestionVersionNo());
        assertEquals(1, question.getOrderNumber());
        assertEquals(25.0, question.getScore(), 0.0);
        assertEquals("Question 91", question.getContent());
        assertEquals("Algebra", question.getTopic());
        assertEquals("HARD", question.getDifficulty());
        assertEquals("images/91.png", question.getIllustrationPath());
        assertEquals("One", question.getAnswerOption1());
        assertEquals("Two", question.getAnswerOption2());
        assertEquals("Three", question.getAnswerOption3());
        assertEquals("Four", question.getAnswerOption4());

        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 10, 9, 12, 30);
        StudentAnswerDTO saved = roundTrip(new StudentAnswerDTO(91, 3, updatedAt));
        assertEquals(91, saved.getQuestionId());
        assertEquals(Integer.valueOf(3), saved.getSelectedOptionNumber());
        assertEquals(updatedAt, saved.getUpdatedAt());

        StudentAnswerDTO unanswered = roundTrip(new StudentAnswerDTO(92, null, null));
        assertNull(unanswered.getSelectedOptionNumber());
        assertNull(unanswered.getUpdatedAt());
    }

    @Test
    public void examAttemptMapsSerializesAndProtectsSuppliedListOrder() throws Exception {
        StudentExamQuestionDTO firstQuestion = question(91, 2);
        StudentExamQuestionDTO secondQuestion = question(92, 5);
        StudentAnswerDTO firstAnswer = new StudentAnswerDTO(
                92, null, LocalDateTime.of(2026, 8, 10, 9, 10)
        );
        StudentAnswerDTO secondAnswer = new StudentAnswerDTO(
                91, 3, LocalDateTime.of(2026, 8, 10, 9, 11)
        );
        List<StudentExamQuestionDTO> questions = new ArrayList<>(
                List.of(firstQuestion, secondQuestion)
        );
        List<StudentAnswerDTO> answers = new ArrayList<>(
                List.of(firstAnswer, secondAnswer)
        );
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 10, 9, 5);
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 10, 10, 50);
        ExamAttemptDTO attempt = new ExamAttemptDTO(
                81, 71, "A7Z9", 31, 4, "Algebra Midterm",
                "Answer every question", startedAt, deadline, 90, 15,
                5400L, SubmissionStatus.IN_PROGRESS, questions, answers
        );

        questions.clear();
        answers.clear();
        assertEquals(List.of(firstQuestion, secondQuestion), attempt.getQuestions());
        assertEquals(List.of(firstAnswer, secondAnswer), attempt.getAnswers());
        assertThrows(UnsupportedOperationException.class,
                () -> attempt.getQuestions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> attempt.getAnswers().clear());

        ExamAttemptDTO restored = roundTrip(attempt);
        assertEquals(81, restored.getSubmissionId());
        assertEquals(71, restored.getExecutionId());
        assertEquals("A7Z9", restored.getExecutionCode());
        assertEquals(31, restored.getExamId());
        assertEquals(4, restored.getExamVersionNo());
        assertEquals("Algebra Midterm", restored.getExamTitle());
        assertEquals("Answer every question", restored.getStudentInstructions());
        assertEquals(startedAt, restored.getStartedAt());
        assertEquals(deadline, restored.getDeadline());
        assertEquals(90, restored.getAllocatedDurationMinutes());
        assertEquals(15, restored.getExtraMinutes());
        assertEquals(5400L, restored.getRemainingSeconds());
        assertEquals(SubmissionStatus.IN_PROGRESS, restored.getStatus());
        assertEquals(91, restored.getQuestions().get(0).getQuestionId());
        assertEquals(92, restored.getQuestions().get(1).getQuestionId());
        assertEquals(92, restored.getAnswers().get(0).getQuestionId());
        assertEquals(91, restored.getAnswers().get(1).getQuestionId());
    }

    @Test
    public void commandPayloadsContainNoAuthenticatedActorIdentityFields() {
        for (Class<?> payloadType : payloadTypes()) {
            Set<String> fields = productionFieldNames(payloadType);
            assertTrue(payloadType.getSimpleName() + " has an actor identity field",
                    fields.stream().noneMatch(FORBIDDEN_PAYLOAD_IDENTITY_FIELDS::contains));
        }
    }

    @Test
    public void studentContractsExposeNoCorrectnessScoresPasswordsOrSetters() {
        assertNoFieldOrMethod(StudentExamQuestionDTO.class, "correctOptionNumber");
        assertNoFieldOrMethod(StudentAnswerDTO.class, "isCorrect");
        assertNoFieldOrMethod(StudentAnswerDTO.class, "scoreReceived");
        assertNoFieldOrMethod(ExamAttemptDTO.class, "automaticScore");
        assertNoFieldOrMethod(ExamAttemptDTO.class, "finalScore");

        for (Class<?> type : allNewContractTypes()) {
            assertNoFieldOrMethod(type, "password");
            assertNoFieldOrMethod(type, "passwordHash");
            assertFalse(type.getSimpleName() + " exposes a setter",
                    Arrays.stream(type.getMethods())
                            .map(Method::getName)
                            .anyMatch(name -> name.startsWith("set")));
        }
    }

    @Test
    public void startPayloadDoesNotOverrideToString() {
        assertFalse(Arrays.stream(StartExamPayload.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch("toString"::equals));
    }

    private static StudentExamQuestionDTO question(int questionId, int versionNo) {
        return new StudentExamQuestionDTO(
                questionId, versionNo, 1, 25.0, "Question " + questionId,
                "Algebra", "HARD", "images/" + questionId + ".png",
                "One", "Two", "Three", "Four"
        );
    }

    private static List<Class<?>> payloadTypes() {
        return List.of(
                ScheduleExamExecutionPayload.class,
                ExecutionCodePayload.class,
                StartExamPayload.class,
                SaveExamAnswerPayload.class,
                SubmissionIdPayload.class,
                ExtendSubmissionTimePayload.class
        );
    }

    private static List<Class<?>> allNewContractTypes() {
        return List.of(
                ScheduleExamExecutionPayload.class,
                ExecutionCodePayload.class,
                StartExamPayload.class,
                SaveExamAnswerPayload.class,
                SubmissionIdPayload.class,
                ExtendSubmissionTimePayload.class,
                ExamExecutionSummaryDTO.class,
                ExamExecutionPreviewDTO.class,
                StudentExamQuestionDTO.class,
                StudentAnswerDTO.class,
                ExamAttemptDTO.class
        );
    }

    private static void assertNoFieldOrMethod(Class<?> type, String name) {
        assertFalse(type.getSimpleName() + " exposes " + name,
                productionFieldNames(type).contains(name)
                        || Arrays.stream(type.getMethods())
                        .map(Method::getName)
                        .anyMatch(method -> method.equalsIgnoreCase("get" + name)
                                || method.equalsIgnoreCase("is" + name)));
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
