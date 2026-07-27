package hsts.server.repository;

import hsts.common.ExtendSubmissionTimePayload;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.STARTED;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.executionSubmissionRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.normalized;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryExtensionTest {
    private static final String LOCK_MARKER =
            "JOIN teacher_courses assignment";
    private static final String UPDATE_MARKER =
            "SET extra_minutes = extra_minutes + ?";
    private static final String AUDIT_MARKER =
            "INSERT INTO submission_time_extensions (";

    @Test
    public void nullAndNonpositiveExtensionFailBeforeConnection() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamSubmissionRepository repository = new ExamSubmissionRepository(database);

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> repository.extendTime(1002, null, STARTED)
        );
        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> repository.extendTime(
                        1002,
                        new ExtendSubmissionTimePayload(501, 0, "Reason"),
                        STARTED
                )
        );

        assertEquals("Time extension data is missing", missing.getMessage());
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
        ExtendSubmissionTimePayload payload = new ExtendSubmissionTimePayload(
                501,
                15,
                reason
        );
        LocalDateTime now = STARTED.plusMinutes(30);

        boolean result = new ExamSubmissionRepository(database)
                .extendTime(1003, payload, now);

        assertTrue(result);
        assertEquals(Map.of(1, 1003, 2, 501), lock.queryExecutions.get(0));
        String lockSql = normalized(lock.sql);
        assertTrue(lockSql.contains("MANAGER.ROLE IN ('TEACHER', 'COORDINATOR')"));
        assertTrue(lockSql.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(lockSql.contains("ASSIGNMENT.COURSE_ID = EXAM.COURSE_ID"));
        assertTrue(lockSql.contains("FOR UPDATE"));

        assertEquals(Map.of(1, 15, 2, reason, 3, 501),
                update.updateExecutions.get(0));
        assertTrue(normalized(update.sql)
                .contains("EXTRA_MINUTES = EXTRA_MINUTES + ?"));
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
    public void inaccessibleOrFinalizedSubmissionReturnsFalseWithoutWrites() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        missingDatabase.plan(LOCK_MARKER).queryRows();
        ExtendSubmissionTimePayload payload = new ExtendSubmissionTimePayload(
                501,
                5,
                "Reason"
        );
        assertFalse(new ExamSubmissionRepository(missingDatabase)
                .extendTime(1002, payload, STARTED.plusMinutes(1)));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController finalDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        finalDatabase.plan(LOCK_MARKER).queryRows(
                executionSubmissionRow("SUBMITTED", STARTED, 75, 0)
        );
        assertFalse(new ExamSubmissionRepository(finalDatabase)
                .extendTime(1002, payload, STARTED.plusMinutes(1)));

        assertTrue(missingDatabase.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
        assertTrue(finalDatabase.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
        assertEquals(1, missingDatabase.commitCount);
        assertEquals(1, finalDatabase.commitCount);
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
                () -> new ExamSubmissionRepository(database).extendTime(
                        1002,
                        new ExtendSubmissionTimePayload(501, 5, "Reason"),
                        STARTED.plusMinutes(80)
                )
        );

        assertEquals("Exam time has expired", thrown.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
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
                () -> new ExamSubmissionRepository(database).extendTime(
                        1002,
                        new ExtendSubmissionTimePayload(501, 5, "Reason"),
                        STARTED.plusMinutes(1)
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
}
