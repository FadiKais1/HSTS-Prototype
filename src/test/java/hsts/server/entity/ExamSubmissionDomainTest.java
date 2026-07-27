package hsts.server.entity;

import hsts.common.type.SubmissionStatus;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionDomainTest {
    private static final LocalDateTime STARTED =
            LocalDateTime.of(2026, 8, 1, 9, 15);

    @Test
    public void startPreservesStableIdentityAndCalculatesDeadlines() {
        ExamSubmission submission = submission();

        assertEquals(0, submission.getSubmissionId());
        assertEquals(81, submission.getExecutionId());
        assertEquals(40, submission.getExamId());
        assertEquals(3, submission.getExamVersionNo());
        assertEquals(1001, submission.getStudentUserId());
        assertEquals(SubmissionStatus.IN_PROGRESS, submission.getStatus());
        assertEquals(STARTED.plusMinutes(75), submission.getOriginalDeadline());
        assertEquals(STARTED.plusMinutes(75), submission.getEffectiveDeadline());
        assertTrue(submission.isEditable(STARTED.plusMinutes(74)));
        assertFalse(submission.isEditable(STARTED.plusMinutes(75)));
    }

    @Test
    public void positiveExtensionsAreCumulativeAndDoNotMoveOriginalDeadline() {
        ExamSubmission submission = submission();

        submission.extendTime(10, " First accommodation ", STARTED.plusMinutes(5));
        submission.extendTime(5, "Second accommodation", STARTED.plusMinutes(6));

        assertEquals(15, submission.getExtraMinutes());
        assertEquals("Second accommodation", submission.getExtensionReason());
        assertEquals(STARTED.plusMinutes(75), submission.getOriginalDeadline());
        assertEquals(STARTED.plusMinutes(90), submission.getEffectiveDeadline());
        assertThrows(
                IllegalArgumentException.class,
                () -> submission.extendTime(0, "Reason", STARTED.plusMinutes(7))
        );
    }

    @Test
    public void answerSavesUpdateOneExactVersionAndPreventDuplicates() {
        ExamSubmission submission = submission();

        submission.saveAnswer(18, 2, 1, STARTED.plusMinutes(1));
        submission.saveAnswer(17, 4, 2, STARTED.plusMinutes(2));
        submission.saveAnswer(17, 4, 3, STARTED.plusMinutes(3));

        assertEquals(2, submission.getStudentAnswers().size());
        assertEquals(17, submission.getStudentAnswers().get(0).getQuestionId());
        assertEquals(3, submission.getStudentAnswers().get(0).getSelectedOptionId());
        assertThrows(
                IllegalArgumentException.class,
                () -> submission.saveAnswer(17, 5, 4, STARTED.plusMinutes(4))
        );
    }

    @Test
    public void answerCollectionAndNestedAnswersAreDefensive() {
        ExamSubmission submission = submission();
        submission.saveAnswer(17, 4, 2, STARTED.plusMinutes(1));

        List<StudentAnswer> exposed = submission.getStudentAnswers();
        assertThrows(
                UnsupportedOperationException.class,
                () -> exposed.add(StudentAnswer.select(
                        0, 18, 2, 1, STARTED.plusMinutes(2)
                ))
        );
        exposed.get(0).selectOption(4, STARTED.plusMinutes(2));

        assertEquals(2, submission.getStudentAnswers().get(0).getSelectedOptionId());
    }

    @Test
    public void deadlineAndFinalizationBlockFurtherAnswerChanges() {
        ExamSubmission expired = submission();
        assertThrows(
                IllegalStateException.class,
                () -> expired.saveAnswer(17, 4, 2, STARTED.plusMinutes(75))
        );

        ExamSubmission submitted = submission();
        submitted.saveAnswer(17, 4, 2, STARTED.plusMinutes(1));
        assertEquals(
                SubmissionStatus.SUBMITTED,
                submitted.submitManually(STARTED.plusMinutes(30))
        );
        assertEquals(STARTED.plusMinutes(30), submitted.getSubmittedAt());
        assertEquals(Integer.valueOf(30), submitted.getActualDurationMinutes());
        assertThrows(
                IllegalStateException.class,
                () -> submitted.saveAnswer(17, 4, 3, STARTED.plusMinutes(31))
        );
        assertThrows(
                IllegalStateException.class,
                () -> submitted.submitManually(STARTED.plusMinutes(31))
        );
    }

    @Test
    public void lateManualSubmissionAndAutomaticSubmissionMatchCurrentPolicy() {
        ExamSubmission late = submission();
        assertEquals(
                SubmissionStatus.AUTO_SUBMITTED,
                late.submitManually(STARTED.plusMinutes(75).plusSeconds(1))
        );

        ExamSubmission automatic = submission();
        assertFalse(automatic.autoSubmit(STARTED.plusMinutes(74)));
        assertTrue(automatic.autoSubmit(STARTED.plusMinutes(75)));
        assertFalse(automatic.autoSubmit(STARTED.plusMinutes(76)));
        assertEquals(SubmissionStatus.AUTO_SUBMITTED, automatic.getStatus());
    }

    @Test
    public void automaticGradeReviewAndPublicationAreControlledAndExact() {
        ExamSubmission submission = submission();
        submission.saveAnswer(17, 4, 2, STARTED.plusMinutes(1));
        submission.submitManually(STARTED.plusMinutes(30));
        submission.recordAnswerGrade(
                17, true, new BigDecimal("33.33"), STARTED.plusMinutes(31)
        );
        submission.recordAutomaticScore(
                new BigDecimal("33.33"), STARTED.plusMinutes(32)
        );

        assertTrue(submission.getPublishedFinalScore().isEmpty());
        assertEquals(0, new BigDecimal("33.33").compareTo(
                submission.getServerFinalScore().orElseThrow()
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> submission.recordTeacherReview(
                        1002,
                        new BigDecimal("40.00"),
                        "Reviewed",
                        " ",
                        STARTED.plusMinutes(33)
                )
        );

        submission.recordTeacherReview(
                1002,
                new BigDecimal("40.00"),
                "Good explanation",
                "Accepted manual work",
                STARTED.plusMinutes(33)
        );
        submission.publish(1002, STARTED.plusMinutes(34));

        assertEquals(SubmissionStatus.PUBLISHED, submission.getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(
                submission.getPublishedFinalScore().orElseThrow()
        ));
        assertThrows(
                IllegalStateException.class,
                () -> submission.recordAutomaticScore(
                        BigDecimal.ZERO,
                        STARTED.plusMinutes(35)
                )
        );
    }

    @Test
    public void hydrationRejectsDuplicateAnswersAndContradictoryLifecycleState() {
        StudentAnswer first = StudentAnswer.rehydrate(
                1, 501, 17, 4, 2, null, null, null,
                STARTED.plusMinutes(1), STARTED.plusMinutes(1)
        );
        StudentAnswer duplicate = StudentAnswer.rehydrate(
                2, 501, 17, 4, 3, null, null, null,
                STARTED.plusMinutes(2), STARTED.plusMinutes(2)
        );
        List<StudentAnswer> answers = new ArrayList<>(List.of(first, duplicate));

        assertThrows(
                IllegalArgumentException.class,
                () -> rehydrate(SubmissionStatus.IN_PROGRESS, null, null, answers)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> rehydrate(
                        SubmissionStatus.SUBMITTED,
                        null,
                        30,
                        List.of()
                )
        );
    }

    @Test
    public void compatibilityConstructorAndUnsafeStatusSetterAreContained() {
        ExamSubmission compatibility = new ExamSubmission();

        assertTrue(compatibility.getStudentAnswers().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> compatibility.isEditable(STARTED)
        );
        assertThrows(
                IllegalStateException.class,
                () -> compatibility.changeSubmissionStatus(
                        SubmissionStatus.SUBMITTED
                )
        );
    }

    private static ExamSubmission submission() {
        return ExamSubmission.start(81, 40, 3, 1001, STARTED, 75);
    }

    private static ExamSubmission rehydrate(
            SubmissionStatus status,
            LocalDateTime submittedAt,
            Integer actualDuration,
            List<StudentAnswer> answers
    ) {
        return ExamSubmission.rehydrate(
                501,
                81,
                40,
                3,
                1001,
                STARTED,
                submittedAt,
                status,
                75,
                0,
                null,
                actualDuration,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                STARTED,
                STARTED.plusMinutes(2),
                answers
        );
    }
}
