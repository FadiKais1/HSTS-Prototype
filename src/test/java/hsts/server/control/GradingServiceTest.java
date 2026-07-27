package hsts.server.control;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.common.type.SubmissionStatus;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.Question;
import hsts.server.entity.StudentAnswer;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GradingServiceTest {
    private static final LocalDateTime EXAM_CREATED =
            LocalDateTime.of(2026, 8, 1, 8, 0);
    private static final LocalDateTime STARTED =
            LocalDateTime.of(2026, 8, 2, 9, 0);
    private static final LocalDateTime SUBMITTED = STARTED.plusMinutes(30);
    private static final LocalDateTime GRADED = SUBMITTED.plusMinutes(1);
    private static final LocalDateTime REVIEWED = GRADED.plusMinutes(1);
    private static final LocalDateTime PUBLISHED = REVIEWED.plusMinutes(1);

    private final GradingService service = new GradingService();

    @Test
    public void allCorrectAndAllWrongUseExactQuestionScores() {
        Exam exam = exactExam("20.00", "30.00", "50.00");
        ExamSubmission correct = submittedSubmission(List.of(
                answer(1, 30, 7, 3),
                answer(2, 10, 2, 1),
                answer(3, 20, 5, 2)
        ));
        ExamSubmission wrong = submittedSubmission(List.of(
                answer(4, 30, 7, 1),
                answer(5, 10, 2, 2),
                answer(6, 20, 5, 4)
        ));

        assertSame(correct, service.gradeAutomatically(correct, exam, GRADED));
        service.gradeAutomatically(wrong, exam, GRADED);

        assertScore("100.00", correct.getAutomaticScoreValue().orElseThrow());
        assertScore("0.00", wrong.getAutomaticScoreValue().orElseThrow());
        assertTrue(correct.getStudentAnswers().stream()
                .allMatch(answer -> answer.getCorrectness().orElseThrow()));
        assertTrue(wrong.getStudentAnswers().stream()
                .noneMatch(answer -> answer.getCorrectness().orElseThrow()));
    }

    @Test
    public void mixedDecimalScoresUseBigDecimalWithoutFloatingPointDrift() {
        Exam exam = exactExam("33.33", "33.33", "33.34");
        ExamSubmission submission = submittedSubmission(List.of(
                answer(1, 30, 7, 3),
                answer(2, 10, 2, 4),
                answer(3, 20, 5, 2)
        ));

        service.gradeAutomatically(submission, exam, GRADED);

        assertScore("66.67", submission.getAutomaticScoreValue().orElseThrow());
        Map<Integer, BigDecimal> scores = answerScores(submission);
        assertScore("33.33", scores.get(30));
        assertScore("0.00", scores.get(10));
        assertScore("33.34", scores.get(20));
    }

    @Test
    public void unansweredQuestionsContributeZeroWithoutFabricatingAnswers() {
        Exam exam = exactExam("20.00", "30.00", "50.00");
        ExamSubmission submission = submittedSubmission(List.of(
                answer(1, 30, 7, 3),
                answer(2, 10, 2, 4)
        ));

        service.gradeAutomatically(submission, exam, GRADED);

        assertScore("20.00", submission.getAutomaticScoreValue().orElseThrow());
        assertEquals(2, submission.getStudentAnswers().size());
        assertTrue(submission.getStudentAnswers().stream()
                .noneMatch(answer -> answer.getQuestionId() == 20));
    }

    @Test
    public void gradingPreservesExamAndAuthoritativeAnswerOrder() {
        Exam exam = exactExam("20.00", "30.00", "50.00");
        List<Integer> examOrderBefore = questionOrder(exam);
        List<BigDecimal> scoresBefore = exam.getExamQuestions().stream()
                .map(ExamQuestion::getScoreValue)
                .toList();
        LocalDateTime examUpdatedBefore = exam.getUpdatedAt();
        ExamSubmission submission = submittedSubmission(List.of(
                answer(1, 30, 7, 3),
                answer(2, 10, 2, 1),
                answer(3, 20, 5, 2)
        ));

        service.gradeAutomatically(submission, exam, GRADED);

        assertEquals(List.of(30, 10, 20), questionOrder(exam));
        assertEquals(examOrderBefore, questionOrder(exam));
        assertEquals(scoresBefore, exam.getExamQuestions().stream()
                .map(ExamQuestion::getScoreValue)
                .toList());
        assertEquals(examUpdatedBefore, exam.getUpdatedAt());
        assertEquals(List.of(30, 10, 20), submission.getStudentAnswers().stream()
                .map(StudentAnswer::getQuestionId)
                .toList());
    }

    @Test
    public void gradingRequiresExactExamAndVersionIdentity() {
        ExamSubmission submission = submittedSubmission(List.of());

        IllegalArgumentException examMismatch = assertThrows(
                IllegalArgumentException.class,
                () -> service.gradeAutomatically(
                        submission,
                        exactExam(41, 3, "20.00", "30.00", "50.00"),
                        GRADED
                )
        );
        IllegalArgumentException versionMismatch = assertThrows(
                IllegalArgumentException.class,
                () -> service.gradeAutomatically(
                        submission,
                        exactExam(40, 4, "20.00", "30.00", "50.00"),
                        GRADED
                )
        );

        assertEquals("Submission exam ID does not match exact exam", examMismatch.getMessage());
        assertEquals(
                "Submission exam version does not match exact exam",
                versionMismatch.getMessage()
        );
    }

    @Test
    public void unknownAndWrongVersionAnswersAreRejectedWithoutCorrectAnswerExposure() {
        Exam exam = exactExam("20.00", "30.00", "50.00");
        ExamSubmission unknown = submittedSubmission(List.of(answer(1, 99, 1, 1)));
        ExamSubmission wrongVersion = submittedSubmission(List.of(answer(2, 30, 8, 3)));

        IllegalArgumentException unknownFailure = assertThrows(
                IllegalArgumentException.class,
                () -> service.gradeAutomatically(unknown, exam, GRADED)
        );
        IllegalArgumentException versionFailure = assertThrows(
                IllegalArgumentException.class,
                () -> service.gradeAutomatically(wrongVersion, exam, GRADED)
        );

        assertEquals(
                "Student answer is not part of the exact exam version: 99",
                unknownFailure.getMessage()
        );
        assertEquals(
                "Student answer version does not match exact exam: 30",
                versionFailure.getMessage()
        );
        assertFalse(unknownFailure.getMessage().toLowerCase().contains("correct option"));
        assertFalse(versionFailure.getMessage().toLowerCase().contains("correct option"));
        assertTrue(unknown.getAutomaticScoreValue().isEmpty());
        assertTrue(wrongVersion.getAutomaticScoreValue().isEmpty());
    }

    @Test
    public void malformedQuestionSnapshotsAreRejectedBeforeSubmissionMutation() {
        ExamSubmission submission = submittedSubmission(List.of(answer(1, 30, 7, 1)));
        Exam noCorrectAnswer = exactExam(List.of(new ExamQuestion(
                30,
                7,
                1,
                new BigDecimal("100.00"),
                question(30, List.of(
                        option(1, false), option(2, false),
                        option(3, false), option(4, false)
                ))
        )));
        Exam onlyThreeOptions = exactExam(List.of(new ExamQuestion(
                30,
                7,
                1,
                new BigDecimal("100.00"),
                question(30, List.of(
                        option(1, true), option(2, false), option(3, false)
                ))
        )));

        assertEquals(
                "Exact question snapshot must contain exactly one correct answer",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.gradeAutomatically(submission, noCorrectAnswer, GRADED)
                ).getMessage()
        );
        assertEquals(
                "Exact question snapshot must contain four answer options",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.gradeAutomatically(submission, onlyThreeOptions, GRADED)
                ).getMessage()
        );
        assertTrue(submission.getAutomaticScoreValue().isEmpty());
        assertTrue(submission.getStudentAnswers().stream()
                .noneMatch(StudentAnswer::isGraded));
    }

    @Test
    public void incompleteExamAndUnsupportedScoreScaleAreRejected() {
        ExamSubmission submission = submittedSubmission(List.of());
        Exam incompleteTotal = exactExam(List.of(new ExamQuestion(
                30, 7, 1, new BigDecimal("99.99"), completeQuestion(30, 3)
        )));
        Exam unsupportedScale = exactExam(List.of(
                new ExamQuestion(
                        30, 7, 1, new BigDecimal("33.333"), completeQuestion(30, 3)
                ),
                new ExamQuestion(
                        10, 2, 2, new BigDecimal("66.667"), completeQuestion(10, 1)
                )
        ));

        assertEquals(
                "Exact exam total score must equal 100.00",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.gradeAutomatically(submission, incompleteTotal, GRADED)
                ).getMessage()
        );
        assertEquals(
                "Exact exam question score exceeds supported decimal scale",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.gradeAutomatically(submission, unsupportedScale, GRADED)
                ).getMessage()
        );
    }

    @Test
    public void submissionMustBeFinalizedAndPublishedResultsAreImmutable() {
        Exam exam = exactExam("20.00", "30.00", "50.00");
        ExamSubmission inProgress = ExamSubmission.start(81, 40, 3, 1001, STARTED, 75);
        ExamSubmission published = gradedSubmission(exam);
        service.reviewGrade(
                published, 1002, new BigDecimal("100.00"), null, null, REVIEWED
        );
        service.publishGrade(published, 1002, PUBLISHED);

        assertEquals(
                "Submission must be finalized before grading",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.gradeAutomatically(inProgress, exam, GRADED)
                ).getMessage()
        );
        assertEquals(
                "Published submissions cannot be regraded",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.gradeAutomatically(
                                published,
                                exam,
                                PUBLISHED.plusMinutes(1)
                        )
                ).getMessage()
        );
    }

    @Test
    public void gradingValidatesRequiredAggregatesAndTimestampBeforeMutation() {
        Exam exam = exactExam("20.00", "30.00", "50.00");
        ExamSubmission submission = submittedSubmission(List.of(
                answer(1, 30, 7, 3),
                answer(2, 10, 2, 1)
        ));

        assertEquals("Submission is required",
                assertThrows(IllegalArgumentException.class, () ->
                        service.gradeAutomatically(null, exam, GRADED)).getMessage());
        assertEquals("Exact exam version is required",
                assertThrows(IllegalArgumentException.class, () ->
                        service.gradeAutomatically(submission, null, GRADED)).getMessage());
        assertEquals("Grading timestamp is required",
                assertThrows(IllegalArgumentException.class, () ->
                        service.gradeAutomatically(submission, exam, null)).getMessage());
        assertEquals("Grading timestamp cannot precede submission state",
                assertThrows(IllegalArgumentException.class, () ->
                        service.gradeAutomatically(
                                submission,
                                exam,
                                SUBMITTED.minusSeconds(1)
                        )).getMessage());
        assertTrue(submission.getAutomaticScoreValue().isEmpty());
        assertTrue(submission.getStudentAnswers().stream()
                .noneMatch(StudentAnswer::isGraded));
    }

    @Test
    public void duplicateAnswerIdentityIsRejectedBySubmissionInvariant() {
        StudentAnswer first = answer(1, 30, 7, 3);
        StudentAnswer duplicate = answer(2, 30, 7, 2);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> submittedSubmission(List.of(first, duplicate))
        );

        assertEquals("Duplicate student answer: 30", failure.getMessage());
    }

    @Test
    public void reviewWithoutAdjustmentRecordsTrimmedFeedbackAndDoesNotPublish() {
        ExamSubmission submission = gradedSubmission(
                exactExam("20.00", "30.00", "50.00")
        );
        List<StudentAnswer> gradedAnswers = submission.getStudentAnswers();

        assertSame(submission, service.reviewGrade(
                submission,
                1002,
                new BigDecimal("100.00"),
                "  Fully correct  ",
                null,
                REVIEWED
        ));

        assertScore("100.00", submission.getServerFinalScore().orElseThrow());
        assertScore("100.00", submission.getAutomaticScoreValue().orElseThrow());
        assertEquals("Fully correct", submission.getTeacherFeedback());
        assertEquals(null, submission.getManualChangeReason());
        assertEquals(Integer.valueOf(1002), submission.getReviewedByUserId());
        assertEquals(REVIEWED, submission.getReviewedAt());
        assertEquals(SubmissionStatus.SUBMITTED, submission.getStatus());
        assertEquals(gradedAnswersState(gradedAnswers),
                gradedAnswersState(submission.getStudentAnswers()));
    }

    @Test
    public void adjustedReviewRequiresAndTrimsAReason() {
        ExamSubmission submission = gradedSubmission(
                exactExam("20.00", "30.00", "50.00")
        );

        assertEquals(
                "Manual score change reason is required",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.reviewGrade(
                                submission,
                                1002,
                                new BigDecimal("95.50"),
                                "  ",
                                REVIEWED
                        )
                ).getMessage()
        );

        service.reviewGrade(
                submission,
                1002,
                new BigDecimal("95.50"),
                "  Accepted written reasoning  ",
                REVIEWED
        );

        assertScore("100.00", submission.getAutomaticScoreValue().orElseThrow());
        assertScore("95.50", submission.getServerFinalScore().orElseThrow());
        assertEquals("Accepted written reasoning", submission.getTeacherFeedback());
        assertEquals("Accepted written reasoning", submission.getManualChangeReason());
    }

    @Test
    public void reviewValidatesScoreIdentityTimeAndAutomaticGrade() {
        ExamSubmission graded = gradedSubmission(exactExam("20.00", "30.00", "50.00"));
        ExamSubmission ungraded = submittedSubmission(List.of());

        assertEquals(
                "Submission score must be between 0 and 100",
                assertThrows(IllegalArgumentException.class, () -> service.reviewGrade(
                        graded, 1002, new BigDecimal("-0.01"), null,
                        "Reason", REVIEWED
                )).getMessage()
        );
        assertEquals(
                "Submission score must be between 0 and 100",
                assertThrows(IllegalArgumentException.class, () -> service.reviewGrade(
                        graded, 1002, new BigDecimal("100.01"), null,
                        "Reason", REVIEWED
                )).getMessage()
        );
        assertEquals("Final score is required",
                assertThrows(IllegalArgumentException.class, () -> service.reviewGrade(
                        graded, 1002, null, null, null, REVIEWED
                )).getMessage());
        assertEquals("Review timestamp is required",
                assertThrows(IllegalArgumentException.class, () -> service.reviewGrade(
                        graded, 1002, new BigDecimal("100.00"), null, null, null
                )).getMessage());
        assertEquals("Reviewer user ID must be positive",
                assertThrows(IllegalArgumentException.class, () -> service.reviewGrade(
                        graded, 0, new BigDecimal("100.00"), null, null, REVIEWED
                )).getMessage());
        assertEquals("Automatic grading must be recorded first",
                assertThrows(IllegalStateException.class, () -> service.reviewGrade(
                        ungraded, 1002, BigDecimal.ZERO, null, null, REVIEWED
                )).getMessage());
    }

    @Test
    public void publicationControlsVisibilityAndIsIdempotent() {
        ExamSubmission submission = gradedSubmission(
                exactExam("20.00", "30.00", "50.00")
        );
        assertTrue(service.getPublishedScore(submission).isEmpty());
        assertEquals(
                "Submission must be reviewed before publication",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.publishGrade(submission, 1002, REVIEWED)
                ).getMessage()
        );

        service.reviewGrade(
                submission, 1002, new BigDecimal("98.25"),
                "Approved feedback", "Manual review", REVIEWED
        );
        List<String> answersBefore = gradedAnswersState(submission.getStudentAnswers());
        assertTrue(service.getPublishedScore(submission).isEmpty());

        assertSame(submission, service.publishGrade(submission, 1003, PUBLISHED));
        assertEquals(SubmissionStatus.PUBLISHED, submission.getStatus());
        assertScore("98.25", service.getPublishedScore(submission).orElseThrow());
        assertScore("100.00", submission.getAutomaticScoreValue().orElseThrow());
        assertScore("98.25", submission.getServerFinalScore().orElseThrow());
        assertEquals(Integer.valueOf(1003), submission.getPublishedByUserId());
        assertEquals(PUBLISHED, submission.getPublishedAt());
        assertEquals(answersBefore, gradedAnswersState(submission.getStudentAnswers()));

        service.publishGrade(submission, 1003, PUBLISHED.plusMinutes(1));
        assertEquals(PUBLISHED, submission.getPublishedAt());
    }

    @Test
    public void publicationRejectsInvalidPublisherAndTimestamp() {
        ExamSubmission submission = gradedSubmission(
                exactExam("20.00", "30.00", "50.00")
        );
        service.reviewGrade(
                submission, 1002, new BigDecimal("100.00"), null, null, REVIEWED
        );

        assertEquals("Publisher user ID must be positive",
                assertThrows(IllegalArgumentException.class, () -> service.publishGrade(
                        submission, 0, PUBLISHED
                )).getMessage());
        assertEquals("Publication timestamp is required",
                assertThrows(IllegalArgumentException.class, () -> service.publishGrade(
                        submission, 1002, null
                )).getMessage());
        assertTrue(service.getPublishedScore(submission).isEmpty());
    }

    @Test
    public void compatibilityMethodsRejectMissingDomainContextWithoutPretendingSuccess() {
        assertThrows(IllegalStateException.class,
                () -> service.calculateAutomaticGrade(1));
        assertThrows(IllegalStateException.class,
                () -> service.updateManualGrade(1, 50.0, "Feedback"));
        assertThrows(IllegalStateException.class,
                () -> service.publishGrade(1));
        assertThrows(IllegalStateException.class,
                () -> service.getSubmissionResult(1));
        assertThrows(IllegalStateException.class,
                () -> service.getResultsByExam(1));
        assertThrows(IllegalStateException.class,
                () -> service.getResultsByStudent(1));
    }

    @Test
    public void serviceHasNoMutableGlobalOrInjectedInfrastructureState() {
        for (Field field : GradingService.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertFalse(field.getType().getName().startsWith("hsts.server.repository"));
            assertFalse(field.getType().getName().startsWith("hsts.common"));
            assertFalse(field.getType().getName().startsWith("hsts.client"));
            assertFalse(field.getType().getName().startsWith("java.sql"));
            assertFalse(field.getType().getName().startsWith("javafx"));
        }
    }

    private static ExamSubmission gradedSubmission(Exam exam) {
        ExamSubmission submission = submittedSubmission(List.of(
                answer(1, 30, 7, 3),
                answer(2, 10, 2, 1),
                answer(3, 20, 5, 2)
        ));
        new GradingService().gradeAutomatically(submission, exam, GRADED);
        return submission;
    }

    private static ExamSubmission submittedSubmission(List<StudentAnswer> answers) {
        return ExamSubmission.rehydrate(
                501,
                81,
                40,
                3,
                1001,
                STARTED,
                SUBMITTED,
                SubmissionStatus.SUBMITTED,
                75,
                0,
                null,
                30,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                STARTED,
                SUBMITTED,
                answers
        );
    }

    private static StudentAnswer answer(
            int answerId,
            int questionId,
            int versionNo,
            int selectedOption
    ) {
        return StudentAnswer.rehydrate(
                answerId,
                501,
                questionId,
                versionNo,
                selectedOption,
                null,
                null,
                null,
                STARTED.plusMinutes(1),
                STARTED.plusMinutes(1)
        );
    }

    private static Exam exactExam(String firstScore, String secondScore,
                                  String thirdScore) {
        return exactExam(40, 3, firstScore, secondScore, thirdScore);
    }

    private static Exam exactExam(int examId, int versionNo,
                                  String firstScore, String secondScore,
                                  String thirdScore) {
        return exactExam(examId, versionNo, List.of(
                new ExamQuestion(30, 7, 1, new BigDecimal(firstScore),
                        completeQuestion(30, 3)),
                new ExamQuestion(10, 2, 2, new BigDecimal(secondScore),
                        completeQuestion(10, 1)),
                new ExamQuestion(20, 5, 3, new BigDecimal(thirdScore),
                        completeQuestion(20, 2))
        ));
    }

    private static Exam exactExam(List<ExamQuestion> questions) {
        return exactExam(40, 3, questions);
    }

    private static Exam exactExam(int examId, int versionNo,
                                  List<ExamQuestion> questions) {
        return Exam.rehydrate(
                examId,
                "ABC123",
                5,
                1002,
                versionNo,
                "Exact approved version",
                75,
                "Teacher notes",
                "Student instructions",
                ExamStatus.APPROVED,
                EXAM_CREATED,
                EXAM_CREATED.plusMinutes(20),
                EXAM_CREATED.plusMinutes(10),
                1003,
                EXAM_CREATED.plusMinutes(20),
                null,
                questions
        );
    }

    private static Question completeQuestion(int questionId, int correctOption) {
        List<AnswerOption> options = new ArrayList<>();
        for (int optionId = 1; optionId <= 4; optionId++) {
            options.add(option(optionId, optionId == correctOption));
        }
        return question(questionId, options);
    }

    private static Question question(int questionId, List<AnswerOption> options) {
        return Question.rehydrate(
                questionId,
                "Question " + questionId,
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.MEDIUM,
                QuestionStatus.ACTIVE,
                EXAM_CREATED,
                EXAM_CREATED,
                "Topic",
                "",
                options
        );
    }

    private static AnswerOption option(int optionId, boolean correct) {
        return new AnswerOption(optionId, "Option " + optionId, correct);
    }

    private static List<Integer> questionOrder(Exam exam) {
        return exam.getExamQuestions().stream()
                .map(ExamQuestion::getQuestionId)
                .toList();
    }

    private static Map<Integer, BigDecimal> answerScores(ExamSubmission submission) {
        Map<Integer, BigDecimal> scores = new LinkedHashMap<>();
        for (StudentAnswer answer : submission.getStudentAnswers()) {
            scores.put(answer.getQuestionId(), answer.getScoreReceivedValue().orElseThrow());
        }
        return scores;
    }

    private static List<String> gradedAnswersState(List<StudentAnswer> answers) {
        return answers.stream()
                .map(answer -> answer.getQuestionId()
                        + ":" + answer.getQuestionVersionNo()
                        + ":" + answer.getSelectedOptionId()
                        + ":" + answer.getCorrectness().orElse(null)
                        + ":" + answer.getScoreReceivedValue().orElse(null))
                .toList();
    }

    private static void assertScore(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
