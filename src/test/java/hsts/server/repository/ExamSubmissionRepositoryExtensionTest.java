package hsts.server.repository;

import hsts.common.type.SubmissionStatus;
import hsts.server.entity.ExamSubmission;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.STARTED;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.executionSubmissionRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.normalized;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.inProgressSubmission;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.planInternalEntity;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryExtensionTest {
    private static final String LOCK_MARKER =
            "JOIN teacher_courses assignment";
    private static final String UPDATE_MARKER = "AND extra_minutes = ?";
    private static final String AUDIT_MARKER =
            "INSERT INTO submission_time_extensions (";

    @Test
    public void nullAndNonpositiveExtensionFailBeforeConnection() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamSubmissionRepository repository = new ExamSubmissionRepository(database);

        NullPointerException missing = assertThrows(
                NullPointerException.class,
                () -> repository.persistExtension(1002, null, 5, "Reason", STARTED)
        );
        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> repository.persistExtension(
                        1002, inProgressSubmission(STARTED, 0),
                        0, "Reason", STARTED
                )
        );

        assertEquals("Exam submission is required", missing.getMessage());
        assertEquals("Extra minutes must be positive", invalid.getMessage());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void authorizedManagerAddsCumulativeMinutesAndImmutableAudit() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController(false);
        ExamRepositoryJdbcTestSupport.StatementPlan lock = database.plan(LOCK_MARKER)
                .queryRows(executionSubmissionRow("IN_PROGRESS", STARTED, 75, 10));
        ExamRepositoryJdbcTestSupport.StatementPlan update = database.plan(UPDATE_MARKER)
                .updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan audit = database.plan(AUDIT_MARKER)
                .updateResults(1);
        String reason = "Approved accommodation - exact text";
        LocalDateTime now = STARTED.plusMinutes(30);
        ExamSubmission extended = extendedSubmission(10, 15, reason, now);
        planInternalEntity(database, "IN_PROGRESS", STARTED, 25);

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistExtension(1003, extended, 15, reason, now);

        assertEquals(25, result.getExtraMinutes());
        assertEquals(Map.of(1, 1003, 2, 501), lock.queryExecutions.get(0));
        String lockSql = normalized(lock.sql);
        assertTrue(lockSql.contains("MANAGER.ROLE IN ('TEACHER', 'COORDINATOR')"));
        assertTrue(lockSql.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(lockSql.contains("ASSIGNMENT.COURSE_ID = EXAM.COURSE_ID"));
        assertTrue(lockSql.contains("FOR UPDATE"));

        assertEquals(Map.of(1, 25, 2, reason, 3, now, 4, 501, 5, 10),
                update.updateExecutions.get(0));
        assertTrue(normalized(update.sql)
                .contains("SET EXTRA_MINUTES = ?"));
        assertEquals(Map.of(1, 501, 2, 15, 3, reason, 4, 1003, 5, now),
                audit.updateExecutions.get(0));
        assertEquals(1, database.connectionRequests);
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertFalse(database.autoCommit);
        assertTrue(database.events.indexOf("update:" + UPDATE_MARKER)
                < database.events.indexOf("update:" + AUDIT_MARKER));
        assertTrue(database.events.indexOf("update:" + AUDIT_MARKER)
                < database.events.indexOf("commit"));
    }

    @Test
    public void inaccessibleOrFinalizedSubmissionUsesEntityErrorsWithoutWrites() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        missingDatabase.plan(LOCK_MARKER).queryRows();
        LocalDateTime now = STARTED.plusMinutes(1);
        ExamSubmission extended = extendedSubmission(0, 5, "Reason", now);
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(missingDatabase)
                        .persistExtension(1002, extended, 5, "Reason", now)
        );
        assertEquals("Exam attempt not found: 501", missing.getMessage());

        ExamRepositoryJdbcTestSupport.FakeDatabaseController finalDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        finalDatabase.plan(LOCK_MARKER).queryRows(
                executionSubmissionRow("SUBMITTED", STARTED, 75, 0)
        );
        IllegalStateException finalized = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(finalDatabase)
                        .persistExtension(1002, extended, 5, "Reason", now)
        );
        assertEquals("Exam attempt already submitted", finalized.getMessage());

        assertTrue(missingDatabase.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
        assertTrue(finalDatabase.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
        assertEquals(1, missingDatabase.rollbackCount);
        assertEquals(1, finalDatabase.rollbackCount);
    }

    @Test
    public void extensionAtOrAfterCurrentDeadlineIsRejected() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(
                executionSubmissionRow("IN_PROGRESS", STARTED, 75, 5)
        );

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database).persistExtension(
                        1002,
                        extendedSubmission(5, 5, "Reason", STARTED.plusMinutes(80)),
                        5, "Reason", STARTED.plusMinutes(80)
                )
        );

        assertEquals("Exam time has expired", thrown.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    @Test
    public void individualExtensionRequiresAuthoritativeExecutionWindow() {
        LocalDateTime currentTime = STARTED.plusMinutes(30);
        for (LocalDateTime[] window : List.of(
                new LocalDateTime[]{currentTime.plusMinutes(1),
                        currentTime.plusHours(1)},
                new LocalDateTime[]{currentTime.minusHours(1), currentTime},
                new LocalDateTime[]{currentTime.minusHours(2),
                        currentTime.minusMinutes(1)}
        )) {
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                    new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
            Map<Object, Object> row = executionSubmissionRow(
                    "IN_PROGRESS", STARTED, 75, 0
            );
            row.put("execution_opening_time", window[0]);
            row.put("execution_closing_time", window[1]);
            database.plan(LOCK_MARKER).queryRows(row);

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new ExamSubmissionRepository(database).persistExtension(
                            1002,
                            extendedSubmission(0, 5, "Reason", currentTime),
                            5,
                            "Reason",
                            currentTime
                    )
            );

            assertEquals("Execution is not open for time extensions",
                    failure.getMessage());
            assertEquals(1, database.rollbackCount);
            assertEquals(0, database.commitCount);
        }
    }

    @Test
    public void auditFailureRollsBackUpdateAndPreservesExactWrapperAndCleanupFailures() {
        SQLException original = new SQLException("audit failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(
                executionSubmissionRow("IN_PROGRESS", STARTED, 75, 0)
        );
        database.plan(UPDATE_MARKER).updateResults(1);
        database.plan(AUDIT_MARKER).updateFailure(original);
        database.rollbackFailure = rollbackFailure;
        database.restorationFailure = restorationFailure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database).persistExtension(
                        1002,
                        extendedSubmission(0, 5, "Reason", STARTED.plusMinutes(1)),
                        5, "Reason", STARTED.plusMinutes(1)
                )
        );

        assertEquals("Failed to extend submission time", thrown.getMessage());
        assertSame(original, thrown.getCause());
        assertEquals(2, original.getSuppressed().length);
        assertSame(rollbackFailure, original.getSuppressed()[0]);
        assertSame(restorationFailure, original.getSuppressed()[1]);
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }

    private static ExamSubmission extendedSubmission(int previousExtraMinutes,
                                                       int addedMinutes,
                                                       String reason,
                                                       LocalDateTime updatedAt) {
        return ExamSubmission.rehydrate(
                501, 81, 40, 3, 1001, STARTED, null,
                SubmissionStatus.IN_PROGRESS, 75,
                previousExtraMinutes + addedMinutes, reason,
                null, null, null, null, null, null, null, null, null,
                STARTED, updatedAt, List.of()
        );
    }
}
