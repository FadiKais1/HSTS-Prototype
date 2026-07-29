package hsts.server.entity;

import hsts.common.type.ExecutionStatus;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionDomainTest {
    private static final LocalDateTime CREATED =
            LocalDateTime.of(2026, 8, 1, 8, 0);
    private static final LocalDateTime OPENING = CREATED.plusHours(1);
    private static final LocalDateTime CLOSING = CREATED.plusHours(4);

    @Test
    public void schedulingPreservesImmutableExactExamVersionAndCode() {
        ExamExecution execution = execution();

        assertEquals(0, execution.getExecutionId());
        assertEquals("A1B2", execution.getExecutionCode());
        assertEquals(40, execution.getExamId());
        assertEquals(3, execution.getExamVersionNo());
        assertEquals(75, execution.getDurationMinutes());
        assertEquals(1002, execution.getCreatedByUserId());
        assertEquals(ExecutionStatus.SCHEDULED, execution.getStatus());
        assertEquals(CREATED, execution.getCreatedAt());
    }

    @Test
    public void executionCodeMustBeExactlyFourUppercaseAlphanumericCharacters() {
        for (String code : new String[]{null, "ABC", "ABCDE", "a1b2", "AB-1"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ExamExecution.schedule(
                            40, 3, code, OPENING, CLOSING, 75, 1002, CREATED
                    )
            );
        }
        ExamExecution execution = execution();
        assertTrue(execution.validateCode("A1B2"));
        assertFalse(execution.validateCode("a1b2"));
        assertFalse(execution.validateCode(" A1B2 "));
    }

    @Test
    public void invalidTimingDurationAndClosedStateAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExamExecution.schedule(
                        40, 3, "A1B2", OPENING, OPENING, 75, 1002, CREATED
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ExamExecution.schedule(
                        40, 3, "A1B2", OPENING, CLOSING, 0, 1002, CREATED
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> rehydrate(
                        ExecutionStatus.CLOSED,
                        null,
                        List.of()
                )
        );
    }

    @Test
    public void newAttemptEligibilityUsesSuppliedTimeAndWindowBoundaries() {
        ExamExecution execution = execution();

        assertFalse(execution.canStartNewAttempt(OPENING.minusNanos(1)));
        assertTrue(execution.canStartNewAttempt(OPENING));
        assertTrue(execution.canStartNewAttempt(CLOSING.minusNanos(1)));
        assertFalse(execution.canStartNewAttempt(CLOSING));
        assertEquals(ExecutionStatus.SCHEDULED,
                execution.statusAt(OPENING.minusNanos(1)));
        assertEquals(ExecutionStatus.OPEN, execution.statusAt(OPENING));
        assertEquals(ExecutionStatus.CLOSED, execution.statusAt(CLOSING));
    }

    @Test
    public void statusTransitionsAreDeterministicAndClosedExecutionCannotReopen() {
        ExamExecution execution = execution();

        assertEquals(ExecutionStatus.OPEN, execution.evaluateStatus(OPENING));
        assertTrue(execution.isOpen());
        assertEquals(ExecutionStatus.CLOSED, execution.evaluateStatus(CLOSING));
        assertFalse(execution.isOpen());
        assertEquals(CLOSING, execution.getClosedAt());
        assertThrows(
                IllegalStateException.class,
                () -> execution.openExecution(CLOSING.plusMinutes(1))
        );
    }

    @Test
    public void executionClosureDoesNotShortenExistingSubmissionDeadline() {
        ExamExecution execution = rehydrate(
                ExecutionStatus.OPEN,
                null,
                List.of()
        );
        LocalDateTime startedAt = CLOSING.minusMinutes(10);
        ExamSubmission submission = ExamSubmission.start(
                81, 40, 3, 1001, startedAt, 75
        );
        execution.addSubmission(submission, startedAt);

        execution.closeExecution(CLOSING);

        assertEquals(
                startedAt.plusMinutes(75),
                execution.getExamSubmissions().get(0).getEffectiveDeadline()
        );
    }

    @Test
    public void submissionRelationshipIsDefensiveImmutableAndRejectsDuplicates() {
        ExamSubmission submission = ExamSubmission.start(
                81, 40, 3, 1001, OPENING.plusMinutes(1), 75
        );
        List<ExamSubmission> source = new ArrayList<>(List.of(submission));
        ExamExecution execution = rehydrate(ExecutionStatus.OPEN, null, source);
        source.clear();

        assertEquals(1, execution.getExamSubmissions().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> execution.getExamSubmissions().add(submission)
        );
        ExamSubmission exposed = execution.getExamSubmissions().get(0);
        exposed.extendTime(5, "Accommodation", OPENING.plusMinutes(2));
        assertEquals(0, execution.getExamSubmissions().get(0).getExtraMinutes());

        assertThrows(
                IllegalArgumentException.class,
                () -> execution.addSubmission(
                        submission,
                        OPENING.plusMinutes(3)
                )
        );
    }

    @Test
    public void statisticsUseBigDecimalAndDefensiveTenDecileSnapshot() {
        ExamExecution execution = execution();
        List<Integer> deciles = new ArrayList<>(
                List.of(1, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        );

        execution.recordStatistics(
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                deciles,
                2,
                1,
                1,
                CREATED.plusMinutes(1)
        );
        deciles.set(0, 99);

        assertEquals(0, new BigDecimal("50.00").compareTo(
                execution.getAverageScoreValue()
        ));
        assertEquals(Integer.valueOf(1), execution.getDecileDistribution().get(0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> execution.getDecileDistribution().add(1)
        );
    }

    @Test
    public void unsafeDiagramOperationsRejectInsteadOfChangingDomainState() {
        ExamExecution execution = execution();

        assertThrows(
                UnsupportedOperationException.class,
                () -> execution.extendTime(5, "Reason")
        );
        assertThrows(IllegalStateException.class, execution::updateStatistics);
        assertEquals(75, execution.getDurationMinutes());
        assertEquals(ExecutionStatus.SCHEDULED, execution.getStatus());
    }

    @Test
    public void executionWideExtensionsAreCumulativeAndPreserveAccessWindow() {
        ExamExecution execution = execution();
        LocalDateTime firstUpdate = CREATED.plusMinutes(1);
        LocalDateTime secondUpdate = CREATED.plusMinutes(2);

        execution.extendForAll(20, "  Accessibility accommodation  ", firstUpdate);
        execution.extendForAll(5, "Additional accommodation", secondUpdate);

        assertEquals(100, execution.getDurationMinutes());
        assertEquals(25, execution.getCumulativeExtensionMinutes());
        assertEquals(OPENING, execution.getOpeningTime());
        assertEquals(CLOSING, execution.getClosingTime());
        assertEquals(secondUpdate, execution.getUpdatedAt());
        assertEquals(ExecutionStatus.SCHEDULED, execution.getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> execution.extendForAll(0, "Reason", secondUpdate));
        assertThrows(IllegalArgumentException.class,
                () -> execution.extendForAll(5, "  ", secondUpdate));
    }

    private static ExamExecution execution() {
        return ExamExecution.schedule(
                40,
                3,
                "A1B2",
                OPENING,
                CLOSING,
                75,
                1002,
                CREATED
        );
    }

    private static ExamExecution rehydrate(
            ExecutionStatus status,
            LocalDateTime closedAt,
            List<ExamSubmission> submissions
    ) {
        return ExamExecution.rehydrate(
                81,
                "A1B2",
                40,
                3,
                OPENING,
                CLOSING,
                75,
                status,
                1002,
                CREATED,
                closedAt,
                null,
                null,
                List.of(),
                submissions.size(),
                0,
                0,
                CREATED,
                submissions
        );
    }
}
