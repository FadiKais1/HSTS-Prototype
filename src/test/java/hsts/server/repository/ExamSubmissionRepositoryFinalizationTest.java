package hsts.server.repository;

import hsts.common.ExamAttemptDTO;
import hsts.common.type.SubmissionStatus;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.STARTED;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.completeSubmissionRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.gradingRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.normalized;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.planSafeAttempt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryFinalizationTest {
    private static final String STUDENT_LOCK_MARKER =
            "AND submission.student_user_id = ? FOR UPDATE";
    private static final String INTERNAL_LOCK_MARKER =
            "WHERE submission.submission_id = ? FOR UPDATE";
    private static final String GRADING_MARKER = "version.correct_option_number";
    private static final String GRADE_UPDATE_MARKER = "SET is_correct = ?";
    private static final String FINALIZE_MARKER = "SET submitted_at = ?";
    private static final String MANUAL_COUNT_MARKER =
            "SET submitted_count = submitted_count + 1";
    private static final String AUTO_COUNT_MARKER =
            "SET auto_submitted_count = auto_submitted_count + 1";

    @Test
    public void manualSubmissionGradesExactImmutableAnswersAndHidesScoresFromDto() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan submission = database.plan(
                STUDENT_LOCK_MARKER
        ).queryRows(completeSubmissionRow("IN_PROGRESS", STARTED, 75, 0));
        ExamRepositoryJdbcTestSupport.StatementPlan grading = database.plan(
                GRADING_MARKER
        ).queryRows(
                gradingRow(17, 4, new BigDecimal("33.33"), 2, 9001, 2),
                gradingRow(18, 2, new BigDecimal("33.33"), 3, 9002, 1),
                gradingRow(19, 5, new BigDecimal("33.34"), 4, null, null)
        );
        ExamRepositoryJdbcTestSupport.StatementPlan answerUpdate = database.plan(
                GRADE_UPDATE_MARKER
        ).updateResults(1, 1);
        ExamRepositoryJdbcTestSupport.StatementPlan finalize = database.plan(
                FINALIZE_MARKER
        ).updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan counter = database.plan(
                MANUAL_COUNT_MARKER
        ).updateResults(1);
        planSafeAttempt(database);
        LocalDateTime now = STARTED.plusMinutes(30).plusSeconds(59);

        ExamAttemptDTO result = new ExamSubmissionRepository(database)
                .submit(1001, 501, now);

        assertEquals(SubmissionStatus.SUBMITTED, result.getStatus());
        assertEquals(0L, result.getRemainingSeconds() - (45L * 60L - 59L));
        assertEquals(Map.of(1, 501, 2, 1001), submission.queryExecutions.get(0));
        assertEquals(Map.of(1, 501, 2, 40, 3, 3), grading.queryExecutions.get(0));
        String gradingSql = normalized(grading.sql);
        assertTrue(gradingSql.contains("SELECTION.QUESTION_VERSION_NO"));
        assertTrue(gradingSql.contains("VERSION.CORRECT_OPTION_NUMBER"));
        assertTrue(gradingSql.contains("ANSWER.SUBMISSION_ID = ?"));
        assertTrue(gradingSql.contains("FOR UPDATE"));

        assertEquals(2, answerUpdate.updateExecutions.size());
        assertGrade(answerUpdate.updateExecutions.get(0), true,
                new BigDecimal("33.33"), 9001);
        assertGrade(answerUpdate.updateExecutions.get(1), false,
                BigDecimal.ZERO, 9002);

        Map<Integer, Object> finalized = finalize.updateExecutions.get(0);
        assertEquals(now, finalized.get(1));
        assertEquals("SUBMITTED", finalized.get(2));
        assertEquals(30, finalized.get(3));
        assertDecimal("33.33", finalized.get(4));
        assertDecimal("33.33", finalized.get(5));
        assertEquals(501, finalized.get(6));
        assertEquals(Map.of(1, 81), counter.updateExecutions.get(0));
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.events.indexOf("update:" + FINALIZE_MARKER)
                < database.events.indexOf("update:" + MANUAL_COUNT_MARKER));
    }

    @Test
    public void lateManualSubmissionBecomesAutomaticAndIncrementsOnlyAutoCounter() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(STUDENT_LOCK_MARKER).queryRows(
                completeSubmissionRow("IN_PROGRESS", STARTED, 75, 0)
        );
        database.plan(GRADING_MARKER).queryRows(
                gradingRow(17, 4, new BigDecimal("100.00"), 2, null, null)
        );
        database.plan(GRADE_UPDATE_MARKER);
        ExamRepositoryJdbcTestSupport.StatementPlan finalize = database.plan(
                FINALIZE_MARKER
        ).updateResults(1);
        database.plan(AUTO_COUNT_MARKER).updateResults(1);
        planSafeAttempt(database);
        LocalDateTime now = STARTED.plusMinutes(75).plusSeconds(1);

        ExamAttemptDTO result = new ExamSubmissionRepository(database)
                .submit(1001, 501, now);

        assertEquals(SubmissionStatus.AUTO_SUBMITTED, result.getStatus());
        assertEquals("AUTO_SUBMITTED", finalize.updateExecutions.get(0).get(2));
        assertDecimal("0", finalize.updateExecutions.get(0).get(4));
        assertEquals(1, database.plans.stream()
                .filter(plan -> plan.marker.equals(AUTO_COUNT_MARKER))
                .findFirst().orElseThrow().updateExecutions.size());
        assertTrue(database.plans.stream()
                .noneMatch(plan -> plan.marker.equals(MANUAL_COUNT_MARKER)));
    }

    @Test
    public void missingAndFinalizedManualSubmissionsUseExactErrors() {
        assertSubmitFailure(null, IllegalArgumentException.class,
                "Exam attempt not found: 501");
        assertSubmitFailure(
                completeSubmissionRow("PUBLISHED", STARTED, 75, 0),
                IllegalStateException.class,
                "Exam attempt already submitted"
        );
    }

    @Test
    public void activeLookupIsStudentScopedSafeAndReturnsEmptyForFinalizedAttempt() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan active = database.plan(
                "AND submission.status = 'IN_PROGRESS'"
        ).queryRows(completeSubmissionRow("IN_PROGRESS", STARTED, 75, 5));
        planSafeAttempt(database);
        LocalDateTime now = STARTED.plusMinutes(20);

        ExamAttemptDTO attempt = new ExamSubmissionRepository(database)
                .findActiveForStudent(1001, 501, now).orElseThrow();

        assertEquals(SubmissionStatus.IN_PROGRESS, attempt.getStatus());
        assertEquals(STARTED.plusMinutes(80), attempt.getDeadline());
        assertEquals(60L * 60L, attempt.getRemainingSeconds());
        assertEquals(Map.of(1, 501, 2, 1001), active.queryExecutions.get(0));
        String sql = normalized(active.sql);
        assertTrue(sql.contains("SUBMISSION.STUDENT_USER_ID = ?"));
        assertTrue(sql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(sql.contains("STUDENT.STATUS = 'ACTIVE'"));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController emptyDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        emptyDatabase.plan("AND submission.status = 'IN_PROGRESS'").queryRows();
        assertTrue(new ExamSubmissionRepository(emptyDatabase)
                .findActiveForStudent(1001, 501, now).isEmpty());
    }

    @Test
    public void expiredIdsUsePersistedDeadlineAndAscendingOrder() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan expired = database.plan(
                "ORDER BY submission_id ASC"
        ).queryRows(row("submission_id", 4), row("submission_id", 9));
        LocalDateTime now = STARTED.plusHours(2);

        List<Integer> ids = new ExamSubmissionRepository(database)
                .findExpiredSubmissionIds(now);

        assertEquals(List.of(4, 9), ids);
        assertEquals(Map.of(1, now), expired.queryExecutions.get(0));
        String sql = normalized(expired.sql);
        assertTrue(sql.contains("STATUS = 'IN_PROGRESS'"));
        assertTrue(sql.contains("ALLOCATED_DURATION_MINUTES + EXTRA_MINUTES"));
        assertTrue(sql.contains("<= ?"));
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    @Test
    public void autoSubmitIsDeadlineGuardedAndIdempotent() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(INTERNAL_LOCK_MARKER)
                .queryRows(completeSubmissionRow("IN_PROGRESS", STARTED, 75, 0))
                .queryRows(completeSubmissionRow("AUTO_SUBMITTED", STARTED, 75, 0));
        database.plan(GRADING_MARKER).queryRows(
                gradingRow(17, 4, new BigDecimal("100.00"), 2, 9001, 2)
        );
        database.plan(GRADE_UPDATE_MARKER).updateResults(1);
        database.plan(FINALIZE_MARKER).updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan counter = database.plan(
                AUTO_COUNT_MARKER
        ).updateResults(1);
        ExamSubmissionRepository repository = new ExamSubmissionRepository(database);
        LocalDateTime now = STARTED.plusMinutes(75);

        assertTrue(repository.autoSubmit(501, now));
        assertFalse(repository.autoSubmit(501, now.plusMinutes(1)));
        assertEquals(1, counter.updateExecutions.size());
        assertEquals(2, database.commitCount);
        assertEquals(0, database.rollbackCount);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController earlyDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        earlyDatabase.plan(INTERNAL_LOCK_MARKER).queryRows(
                completeSubmissionRow("IN_PROGRESS", STARTED, 75, 0)
        );
        assertFalse(new ExamSubmissionRepository(earlyDatabase)
                .autoSubmit(501, STARTED.plusMinutes(74)));
        assertTrue(earlyDatabase.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    @Test
    public void readAndWriteJdbcFailuresUseExactWrappersAndPreserveCause() {
        assertConnectionFailure(
                "Failed to load active exam attempt",
                repository -> repository.findActiveForStudent(1001, 501, STARTED)
        );
        assertConnectionFailure(
                "Failed to load expired submissions",
                repository -> repository.findExpiredSubmissionIds(STARTED)
        );
        assertConnectionFailure(
                "Failed to submit exam attempt",
                repository -> repository.submit(1001, 501, STARTED)
        );
        assertConnectionFailure(
                "Failed to auto-submit exam attempt",
                repository -> repository.autoSubmit(501, STARTED)
        );
    }

    private static void assertSubmitFailure(Map<Object, Object> submissionRow,
                                            Class<? extends RuntimeException> type,
                                            String message) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan submission = database.plan(
                STUDENT_LOCK_MARKER
        );
        if (submissionRow == null) {
            submission.queryRows();
        } else {
            submission.queryRows(submissionRow);
        }

        RuntimeException thrown = assertThrows(
                type,
                () -> new ExamSubmissionRepository(database)
                        .submit(1001, 501, STARTED.plusMinutes(1))
        );

        assertEquals(message, thrown.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }

    private static void assertConnectionFailure(String message, RepositoryCall call) {
        SQLException failure = new SQLException("database unavailable");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.connectionFailure = failure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> call.execute(new ExamSubmissionRepository(database))
        );

        assertEquals(message, thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }

    private static void assertGrade(Map<Integer, Object> values, boolean correct,
                                    BigDecimal score, int answerId) {
        assertEquals(correct, values.get(1));
        assertEquals(0, ((BigDecimal) values.get(2)).compareTo(score));
        assertEquals(answerId, values.get(3));
        assertEquals(501, values.get(4));
    }

    private static void assertDecimal(String expected, Object actual) {
        assertEquals(0, ((BigDecimal) actual).compareTo(new BigDecimal(expected)));
    }

    @FunctionalInterface
    private interface RepositoryCall {
        void execute(ExamSubmissionRepository repository);
    }
}
