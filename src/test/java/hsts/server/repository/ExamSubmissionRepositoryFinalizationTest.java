package hsts.server.repository;

import hsts.common.ExamAttemptDTO;
import hsts.common.type.SubmissionStatus;
import hsts.server.entity.ExamSubmission;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.STARTED;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.completeSubmissionRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.normalized;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.planSafeAttempt;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.submissionEntityRow;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryFinalizationTest {
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
    public void expiredEntityReadUsesPersistedDeadlineAndAscendingOrder() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan expired = database.plan(
                "ORDER BY TIMESTAMPADD("
        ).queryRows(
                submissionEntityRow(4, "IN_PROGRESS", STARTED, 0),
                submissionEntityRow(9, "IN_PROGRESS", STARTED, 5)
        );
        database.plan("ORDER BY selection.order_number ASC,")
                .queryRows()
                .queryRows();
        LocalDateTime now = STARTED.plusHours(2);

        List<ExamSubmission> submissions = new ExamSubmissionRepository(database)
                .findExpiredInProgressEntities(now);

        assertEquals(List.of(4, 9), submissions.stream()
                .map(ExamSubmission::getSubmissionId).toList());
        assertEquals(Map.of(1, now), expired.queryExecutions.get(0));
        String sql = normalized(expired.sql);
        assertTrue(sql.contains("SUBMISSION.STATUS = 'IN_PROGRESS'"));
        assertTrue(sql.contains("ALLOCATED_DURATION_MINUTES + SUBMISSION.EXTRA_MINUTES"));
        assertTrue(sql.contains("<= ?"));
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    @Test
    public void retainedReadJdbcFailuresUseExactWrappersAndPreserveCause() {
        assertConnectionFailure(
                "Failed to load active exam attempt",
                repository -> repository.findActiveForStudent(1001, 501, STARTED)
        );
        assertConnectionFailure(
                "Failed to load expired exam submission entities",
                repository -> repository.findExpiredInProgressEntities(STARTED)
        );
    }

    private static void assertConnectionFailure(String message, RepositoryCall call) {
        SQLException failure = new SQLException("database unavailable");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.connectionFailure = failure;

        IllegalStateException thrown;
        try {
            call.execute(new ExamSubmissionRepository(database));
            throw new AssertionError("Expected repository failure");
        } catch (IllegalStateException exception) {
            thrown = exception;
        }

        assertEquals(message, thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }

    @FunctionalInterface
    private interface RepositoryCall {
        void execute(ExamSubmissionRepository repository);
    }
}
