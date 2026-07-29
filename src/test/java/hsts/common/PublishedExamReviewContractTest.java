package hsts.common;

import hsts.common.type.PublishedAnswerOutcome;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PublishedExamReviewContractTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 12, 0);

    @Test
    public void exactOutcomeValuesAndImmutableSerializableRoundTrip() throws Exception {
        assertEquals(
                List.of("CORRECT", "INCORRECT", "UNANSWERED"),
                Arrays.stream(PublishedAnswerOutcome.values()).map(Enum::name).toList()
        );
        PublishedExamQuestionReviewDTO question = question(
                1, 30, 4, 2, 2, PublishedAnswerOutcome.CORRECT,
                new BigDecimal("25.00")
        );
        PublishedExamReviewDTO source = review(List.of(question));
        PublishedExamReviewDTO copy = roundTrip(source);

        assertEquals(501, copy.getSubmissionId());
        assertEquals(81, copy.getExecutionId());
        assertEquals("RLYQ", copy.getExecutionCode());
        assertEquals(40, copy.getExamId());
        assertEquals(3, copy.getExamVersionNo());
        assertEquals("Historical Final", copy.getExamTitle());
        assertEquals(7, copy.getCourseId());
        assertEquals("Mathematics", copy.getCourseName());
        assertEquals(new BigDecimal("88.50"), copy.getFinalScore());
        assertEquals("Good work", copy.getTeacherFeedback());
        assertEquals(NOW.minusHours(1), copy.getSubmittedAt());
        assertEquals(NOW.minusMinutes(30), copy.getReviewedAt());
        assertEquals(NOW, copy.getPublishedAt());
        assertEquals(List.of("One", "Two", "Three", "Four"),
                copy.getQuestions().get(0).getAnswerOptions());
        assertThrows(UnsupportedOperationException.class,
                () -> copy.getQuestions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> copy.getQuestions().get(0).getAnswerOptions().clear());
    }

    @Test
    public void questionOrderAndDefensiveListSnapshotsArePreserved() {
        List<String> options = new ArrayList<>(List.of("A", "B", "C", "D"));
        PublishedExamQuestionReviewDTO first = new PublishedExamQuestionReviewDTO(
                1, 30, 9, "First", "Topic", "HARD", "MULTIPLE_CHOICE", "",
                options, null, 4, PublishedAnswerOutcome.UNANSWERED,
                new BigDecimal("0.00"), new BigDecimal("40.00")
        );
        options.set(0, "Changed");
        PublishedExamQuestionReviewDTO second = question(
                2, 10, 2, 1, 3, PublishedAnswerOutcome.INCORRECT,
                new BigDecimal("0.00")
        );
        List<PublishedExamQuestionReviewDTO> questions = new ArrayList<>(
                List.of(first, second)
        );
        PublishedExamReviewDTO review = review(questions);
        questions.clear();

        assertEquals(List.of(30, 10), review.getQuestions().stream()
                .map(PublishedExamQuestionReviewDTO::getQuestionId).toList());
        assertEquals("A", review.getQuestions().get(0).getAnswerOptions().get(0));
        assertEquals(new BigDecimal("0.00"), first.getAwardedScore());
        assertEquals(new BigDecimal("40.00"), first.getMaximumScore());
    }

    @Test
    public void invalidIdentityOrderOutcomeAndScoresAreRejected() {
        PublishedExamQuestionReviewDTO first = question(
                1, 30, 4, 2, 2, PublishedAnswerOutcome.CORRECT,
                new BigDecimal("25.00")
        );
        PublishedExamQuestionReviewDTO duplicate = question(
                2, 30, 4, null, 2, PublishedAnswerOutcome.UNANSWERED,
                new BigDecimal("0.00")
        );
        assertThrows(IllegalArgumentException.class,
                () -> review(List.of(first, duplicate)));
        PublishedExamQuestionReviewDTO wrongOrder = question(
                3, 20, 1, null, 2, PublishedAnswerOutcome.UNANSWERED,
                new BigDecimal("0.00")
        );
        assertThrows(IllegalArgumentException.class,
                () -> review(List.of(first, wrongOrder)));
        assertThrows(IllegalArgumentException.class, () -> question(
                1, 30, 4, 1, 2, PublishedAnswerOutcome.CORRECT,
                new BigDecimal("25.00")
        ));
        assertThrows(IllegalArgumentException.class, () -> question(
                1, 30, 4, null, 2, PublishedAnswerOutcome.INCORRECT,
                new BigDecimal("0.00")
        ));
        assertThrows(IllegalArgumentException.class, () -> question(
                1, 30, 4, 2, 2, PublishedAnswerOutcome.CORRECT,
                new BigDecimal("25.01")
        ));
    }

    @Test
    public void transportSurfaceContainsNoSensitiveOrServerFields() {
        Set<String> fieldNames = Arrays.stream(PublishedExamReviewDTO.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName).collect(Collectors.toSet());
        Set<String> questionFields = Arrays.stream(
                        PublishedExamQuestionReviewDTO.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName).collect(Collectors.toSet());
        for (String forbidden : List.of(
                "studentId", "reviewerUserId", "publisherUserId", "automaticScore",
                "adjustmentReason", "password", "passwordHash", "provider"
        )) {
            assertFalse(fieldNames.contains(forbidden));
            assertFalse(questionFields.contains(forbidden));
        }
        assertTrue(Arrays.stream(PublishedExamReviewDTO.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
    }

    private static PublishedExamReviewDTO review(
            List<PublishedExamQuestionReviewDTO> questions
    ) {
        return new PublishedExamReviewDTO(
                501, 81, "RLYQ", 40, 3, "Historical Final", 7,
                "Mathematics", new BigDecimal("88.50"), "Good work",
                NOW.minusHours(1), NOW.minusMinutes(30), NOW, questions
        );
    }

    private static PublishedExamQuestionReviewDTO question(
            int order, int questionId, int version, Integer selected,
            int correct, PublishedAnswerOutcome outcome, BigDecimal awarded
    ) {
        return new PublishedExamQuestionReviewDTO(
                order, questionId, version, "Question " + questionId,
                "Algebra", "HARD", "MULTIPLE_CHOICE", "",
                List.of("One", "Two", "Three", "Four"), selected, correct,
                outcome, awarded, new BigDecimal("25.00")
        );
    }

    private static PublishedExamReviewDTO roundTrip(PublishedExamReviewDTO source)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(source);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())
        )) {
            return (PublishedExamReviewDTO) input.readObject();
        }
    }
}
