package hsts.common;

import hsts.common.type.SubmissionStatus;
import hsts.server.net.Server;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GradeReviewContractTest {
    private static final LocalDateTime STARTED =
            LocalDateTime.of(2026, 8, 12, 9, 0);
    private static final LocalDateTime SUBMITTED = STARTED.plusMinutes(45);
    private static final LocalDateTime REVIEWED = SUBMITTED.plusMinutes(5);
    private static final LocalDateTime PUBLISHED = REVIEWED.plusMinutes(5);

    private static final List<Class<?>> CONTRACT_TYPES = List.of(
            ExecutionSubmissionSummaryDTO.class,
            SubmissionAnswerReviewDTO.class,
            SubmissionReviewDTO.class,
            ReviewSubmissionPayload.class,
            PublishSubmissionPayload.class,
            ExecutionIdPayload.class,
            PublishedGradeSummaryDTO.class,
            PublishedGradeDTO.class
    );

    @Test
    public void executionSummaryMapsSerializesAndPreservesNullableValues()
            throws Exception {
        BigDecimal automatic = new BigDecimal("87.500");
        ExecutionSubmissionSummaryDTO restored = roundTrip(
                new ExecutionSubmissionSummaryDTO(
                        501, 81, 40, 3, "Algebra Final", 1001,
                        "Development Student", SubmissionStatus.SUBMITTED,
                        automatic, null, STARTED, SUBMITTED, null, null
                )
        );

        assertEquals(501, restored.getSubmissionId());
        assertEquals(81, restored.getExecutionId());
        assertEquals(40, restored.getExamId());
        assertEquals(3, restored.getExamVersionNo());
        assertEquals("Algebra Final", restored.getExamTitle());
        assertEquals(1001, restored.getStudentUserId());
        assertEquals("Development Student", restored.getStudentName());
        assertEquals(SubmissionStatus.SUBMITTED, restored.getStatus());
        assertEquals(automatic, restored.getAutomaticScore());
        assertEquals(3, restored.getAutomaticScore().scale());
        assertNull(restored.getFinalScore());
        assertEquals(STARTED, restored.getStartedAt());
        assertEquals(SUBMITTED, restored.getSubmittedAt());
        assertNull(restored.getReviewedAt());
        assertNull(restored.getPublishedAt());
    }

    @Test
    public void answerReviewMapsSerializesCompleteAndNullableFields()
            throws Exception {
        BigDecimal maximum = new BigDecimal("25.00");
        SubmissionAnswerReviewDTO answered = roundTrip(
                new SubmissionAnswerReviewDTO(
                        17, 4, 2, "What is 2 + 2?", "Four", true,
                        new BigDecimal("25.00"), maximum
                )
        );
        SubmissionAnswerReviewDTO unanswered = roundTrip(
                new SubmissionAnswerReviewDTO(
                        18, 2, 3, "What is 3 + 3?", null, null, null,
                        maximum
                )
        );

        assertEquals(17, answered.getQuestionId());
        assertEquals(4, answered.getQuestionVersionNo());
        assertEquals(2, answered.getOrderNumber());
        assertEquals("What is 2 + 2?", answered.getQuestionContent());
        assertEquals("Four", answered.getSelectedOptionText());
        assertEquals(Boolean.TRUE, answered.getCorrect());
        assertEquals(new BigDecimal("25.00"), answered.getAwardedScore());
        assertEquals(maximum, answered.getMaximumScore());
        assertNull(unanswered.getSelectedOptionText());
        assertNull(unanswered.getCorrect());
        assertNull(unanswered.getAwardedScore());
    }

    @Test
    public void submissionReviewMapsSerializesAndProtectsAnswerOrder()
            throws Exception {
        SubmissionAnswerReviewDTO first = answer(30, 1);
        SubmissionAnswerReviewDTO second = answer(10, 2);
        List<SubmissionAnswerReviewDTO> supplied = new ArrayList<>(
                List.of(first, second)
        );
        SubmissionReviewDTO review = new SubmissionReviewDTO(
                501, 81, 40, 3, "Algebra Final", 1001,
                "Development Student", SubmissionStatus.SUBMITTED,
                new BigDecimal("60.00"), new BigDecimal("65.00"),
                "Reviewed response", "Accepted ambiguity", 1002,
                STARTED, SUBMITTED, REVIEWED, 0, null, supplied
        );

        supplied.clear();
        assertEquals(List.of(first, second), review.getAnswers());
        assertThrows(UnsupportedOperationException.class,
                () -> review.getAnswers().clear());

        SubmissionReviewDTO restored = roundTrip(review);
        assertEquals(501, restored.getSubmissionId());
        assertEquals(81, restored.getExecutionId());
        assertEquals(40, restored.getExamId());
        assertEquals(3, restored.getExamVersionNo());
        assertEquals("Algebra Final", restored.getExamTitle());
        assertEquals(1001, restored.getStudentUserId());
        assertEquals("Development Student", restored.getStudentName());
        assertEquals(SubmissionStatus.SUBMITTED, restored.getStatus());
        assertEquals(new BigDecimal("60.00"), restored.getAutomaticScore());
        assertEquals(new BigDecimal("65.00"), restored.getFinalScore());
        assertEquals("Reviewed response", restored.getTeacherFeedback());
        assertEquals("Accepted ambiguity", restored.getAdjustmentReason());
        assertEquals(1002, restored.getReviewerUserId());
        assertEquals(STARTED, restored.getStartedAt());
        assertEquals(SUBMITTED, restored.getSubmittedAt());
        assertEquals(REVIEWED, restored.getReviewedAt());
        assertEquals(0, restored.getPublisherUserId());
        assertNull(restored.getPublishedAt());
        assertEquals(30, restored.getAnswers().get(0).getQuestionId());
        assertEquals(10, restored.getAnswers().get(1).getQuestionId());
    }

    @Test
    public void submissionReviewRejectsNullAnswerStorage() {
        assertThrows(NullPointerException.class, () -> reviewWithAnswers(null));
        assertThrows(NullPointerException.class,
                () -> reviewWithAnswers(Arrays.asList(answer(1, 1), null)));
        assertTrue(reviewWithAnswers(List.of()).getAnswers().isEmpty());
    }

    @Test
    public void mutationPayloadsMapSerializeWithoutActorIdentity() throws Exception {
        BigDecimal score = new BigDecimal("91.250");
        ReviewSubmissionPayload review = roundTrip(new ReviewSubmissionPayload(
                501, score, "Clear work", "Accepted alternate method", REVIEWED
        ));
        PublishSubmissionPayload publication = roundTrip(
                new PublishSubmissionPayload(501, PUBLISHED)
        );
        ExecutionIdPayload execution = roundTrip(new ExecutionIdPayload(81));

        assertEquals(501, review.getSubmissionId());
        assertEquals(score, review.getFinalScore());
        assertEquals(3, review.getFinalScore().scale());
        assertEquals("Clear work", review.getFeedback());
        assertEquals("Accepted alternate method", review.getAdjustmentReason());
        assertEquals(REVIEWED, review.getExpectedUpdatedAt());
        assertEquals(501, publication.getSubmissionId());
        assertEquals(PUBLISHED, publication.getExpectedUpdatedAt());
        assertEquals(81, execution.getExecutionId());

        assertEquals(
                Set.of("submissionId", "finalScore", "feedback",
                        "adjustmentReason", "expectedUpdatedAt"),
                productionFieldNames(ReviewSubmissionPayload.class)
        );
        assertEquals(
                Set.of("submissionId", "expectedUpdatedAt"),
                productionFieldNames(PublishSubmissionPayload.class)
        );
    }

    @Test
    public void publishedGradeContractsMapAndSerializeOnlyPublishedResultData()
            throws Exception {
        BigDecimal score = new BigDecimal("92.750");
        PublishedGradeSummaryDTO summary = roundTrip(
                new PublishedGradeSummaryDTO(
                        501, 81, 40, 3, "Algebra Final", "Algebra",
                        score, SUBMITTED, PUBLISHED
                )
        );
        PublishedGradeDTO detail = roundTrip(new PublishedGradeDTO(
                501, 81, 40, 3, "Algebra Final", "Algebra",
                SubmissionStatus.PUBLISHED, score, "Strong work",
                SUBMITTED, REVIEWED, PUBLISHED
        ));

        assertEquals(501, summary.getSubmissionId());
        assertEquals(81, summary.getExecutionId());
        assertEquals(40, summary.getExamId());
        assertEquals(3, summary.getExamVersionNo());
        assertEquals("Algebra Final", summary.getExamTitle());
        assertEquals("Algebra", summary.getCourseName());
        assertEquals(score, summary.getFinalScore());
        assertEquals(3, summary.getFinalScore().scale());
        assertEquals(SUBMITTED, summary.getSubmittedAt());
        assertEquals(PUBLISHED, summary.getPublishedAt());

        assertEquals(501, detail.getSubmissionId());
        assertEquals(81, detail.getExecutionId());
        assertEquals(40, detail.getExamId());
        assertEquals(3, detail.getExamVersionNo());
        assertEquals("Algebra Final", detail.getExamTitle());
        assertEquals("Algebra", detail.getCourseName());
        assertEquals(SubmissionStatus.PUBLISHED, detail.getStatus());
        assertEquals(score, detail.getFinalScore());
        assertEquals("Strong work", detail.getTeacherFeedback());
        assertEquals(SUBMITTED, detail.getSubmittedAt());
        assertEquals(REVIEWED, detail.getReviewedAt());
        assertEquals(PUBLISHED, detail.getPublishedAt());
    }

    @Test
    public void contractsAreSerializableImmutableAndExposeNoForbiddenData() {
        for (Class<?> type : CONTRACT_TYPES) {
            assertTrue(type.getSimpleName() + " is not serializable",
                    Serializable.class.isAssignableFrom(type));
            for (Field field : type.getDeclaredFields()) {
                assertTrue(type.getSimpleName() + "." + field.getName()
                                + " is not final",
                        Modifier.isFinal(field.getModifiers()));
                assertFalse(type.getSimpleName() + " references a server entity",
                        field.getGenericType().getTypeName()
                                .startsWith("hsts.server.entity"));
            }
            assertFalse(type.getSimpleName() + " exposes a setter",
                    Arrays.stream(type.getMethods())
                            .map(Method::getName)
                            .anyMatch(name -> name.startsWith("set")));
        }

        assertNoNames(SubmissionAnswerReviewDTO.class,
                "correctoption", "password", "identity");
        assertNoNames(PublishedGradeSummaryDTO.class,
                "automaticscore", "correct", "awardedscore", "adjustmentreason",
                "reviewer", "publisher", "correctoption", "password", "identity");
        assertNoNames(PublishedGradeDTO.class,
                "automaticscore", "correct", "awardedscore", "adjustmentreason",
                "reviewer", "publisher", "correctoption", "password", "identity",
                "answers");
    }

    @Test
    public void requestTypesExistAndContextFreeServerRequiresAuthentication() {
        List<RequestType> types = List.of(
                RequestType.LIST_EXECUTION_SUBMISSIONS,
                RequestType.GET_SUBMISSION_FOR_REVIEW,
                RequestType.REVIEW_SUBMISSION_GRADE,
                RequestType.PUBLISH_SUBMISSION_GRADE,
                RequestType.LIST_MY_PUBLISHED_GRADES,
                RequestType.GET_MY_PUBLISHED_GRADE
        );
        Server server = new Server(0, null, null, null);

        for (RequestType type : types) {
            Response response = server.handleRequest(new Request(type, null));
            assertFalse(response.isSuccess());
            assertEquals("Authentication context required", response.getMessage());
        }
    }

    private static SubmissionAnswerReviewDTO answer(int questionId,
                                                     int orderNumber) {
        return new SubmissionAnswerReviewDTO(
                questionId, 2, orderNumber, "Question " + questionId,
                "Option", true, new BigDecimal("10.00"),
                new BigDecimal("10.00")
        );
    }

    private static SubmissionReviewDTO reviewWithAnswers(
            List<SubmissionAnswerReviewDTO> answers
    ) {
        return new SubmissionReviewDTO(
                501, 81, 40, 3, "Algebra Final", 1001,
                "Development Student", SubmissionStatus.SUBMITTED,
                null, null, null, null, 0, STARTED, null, null,
                0, null, answers
        );
    }

    private static void assertNoNames(Class<?> type, String... forbiddenTokens) {
        List<String> names = new ArrayList<>();
        Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.getName().equals("serialVersionUID"))
                .map(Field::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(names::add);
        Arrays.stream(type.getMethods())
                .map(Method::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(names::add);
        for (String token : forbiddenTokens) {
            assertTrue(type.getSimpleName() + " exposes forbidden token " + token,
                    names.stream().noneMatch(name -> name.contains(token)));
        }
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
