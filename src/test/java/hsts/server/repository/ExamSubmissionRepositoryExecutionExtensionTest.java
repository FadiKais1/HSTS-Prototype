package hsts.server.repository;

import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryExecutionExtensionTest {
    private static final String LOCK = "FROM exam_executions execution";
    private static final String UPDATE_EXECUTION = "SET duration_minutes = ?";
    private static final String ACTIVE_STUDENTS = "SELECT student_user_id";
    private static final String UPDATE_SUBMISSIONS =
            "SET allocated_duration_minutes = allocated_duration_minutes + ?";
    private static final String AUDIT = "INSERT INTO execution_time_extensions";
    private static final String NOTIFICATION = "INSERT INTO notifications";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 1, 10, 0);

    @Test
    public void extensionUpdatesExecutionActiveAttemptsAuditAndRecipientsAtomically() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        ExamRepositoryJdbcTestSupport.StatementPlan lock = database.plan(LOCK)
                .queryRows(targetRow(NOW, NOW.plusHours(1)));
        ExamRepositoryJdbcTestSupport.StatementPlan execution =
                database.plan(UPDATE_EXECUTION).updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan students =
                database.plan(ACTIVE_STUDENTS)
                        .queryRows(row("student_user_id", 1001),
                                row("student_user_id", 1005));
        ExamRepositoryJdbcTestSupport.StatementPlan submissions =
                database.plan(UPDATE_SUBMISSIONS).updateResults(2);
        ExamRepositoryJdbcTestSupport.StatementPlan audit = database.plan(AUDIT)
                .updateResults(1).generatedKey(77);
        ExamRepositoryJdbcTestSupport.StatementPlan notifications =
                database.plan(NOTIFICATION).updateResults(1, 1);

        int recipients = new ExamSubmissionRepository(database)
                .extendExecutionForAll(1002, 81, 20, "  Accommodation  ", NOW);

        assertEquals(2, recipients);
        assertEquals(Map.of(1, 1002, 2, 81), lock.queryExecutions.get(0));
        assertTrue(lock.sql.contains("manager.role = 'TEACHER'"));
        assertTrue(lock.sql.contains("manager.role = 'COORDINATOR'"));
        assertTrue(lock.sql.contains("FOR UPDATE"));
        assertEquals(Map.of(1, 95, 2, 20, 3, NOW, 4, 81, 5, 75, 6, 0),
                execution.updateExecutions.get(0));
        assertEquals(Map.of(1, 81), students.queryExecutions.get(0));
        assertEquals(Map.of(1, 20, 2, NOW, 3, 81),
                submissions.updateExecutions.get(0));
        assertEquals(Map.of(1, 81, 2, 20, 3, "Accommodation", 4, 1002, 5, NOW),
                audit.updateExecutions.get(0));
        assertEquals(2, notifications.updateExecutions.size());
        assertEquals(1001, notifications.updateExecutions.get(0).get(1));
        assertEquals(1005, notifications.updateExecutions.get(1).get(1));
        assertEquals("EXECUTION_EXTENDED",
                notifications.updateExecutions.get(0).get(2));
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.autoCommit);
        assertTrue(database.events.indexOf("update:" + NOTIFICATION)
                < database.events.indexOf("commit"));
        assertTrue(submissions.sql.contains("status = 'IN_PROGRESS'"));
    }

    @Test
    public void storedScheduledExecutionUsesServerWindowBoundariesForExtension() {
        assertWindowRejected(NOW.plusMinutes(1), NOW.plusHours(1), NOW,
                "before opening");
        assertWindowRejected(NOW.minusHours(1), NOW, NOW,
                "at closing");
        assertWindowRejected(NOW.minusHours(2), NOW.minusMinutes(1), NOW,
                "after closing");
    }

    @Test
    public void notificationFailureRollsBackEveryExtensionWriteAndPreservesCause() {
        SQLException original = new SQLException("notification insert failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(LOCK).queryRows(targetRow());
        database.plan(UPDATE_EXECUTION).updateResults(1);
        database.plan(ACTIVE_STUDENTS).queryRows(row("student_user_id", 1001));
        database.plan(UPDATE_SUBMISSIONS).updateResults(1);
        database.plan(AUDIT).updateResults(1).generatedKey(77);
        database.plan(NOTIFICATION).updateFailure(original);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database)
                        .extendExecutionForAll(1003, 81, 15, "Reason", NOW)
        );

        assertEquals("Failed to extend exam execution", failure.getMessage());
        assertSame(original, failure.getCause());
        assertEquals(0, database.commitCount);
        assertEquals(1, database.rollbackCount);
        assertTrue(database.autoCommit);
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController database() {
        return new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
    }

    private static Map<Object, Object> targetRow() {
        return targetRow(NOW.minusHours(1), NOW.plusHours(1));
    }

    private static Map<Object, Object> targetRow(LocalDateTime opening,
                                                  LocalDateTime closing) {
        return row(
                "execution_id", 81,
                "execution_code", "RLYQ",
                "exam_id", 40,
                "exam_version_no", 3,
                "exam_title", "Algebra Midterm",
                "opening_time", opening,
                "closing_time", closing,
                "duration_minutes", 75,
                "cumulative_extension_minutes", 0,
                "status", "SCHEDULED"
        );
    }

    private static void assertWindowRejected(LocalDateTime opening,
                                             LocalDateTime closing,
                                             LocalDateTime serverTime,
                                             String caseName) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(LOCK).queryRows(targetRow(opening, closing));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database)
                        .extendExecutionForAll(
                                1002, 81, 5, "Reason", serverTime
                        )
        );

        assertEquals(caseName, "Execution is not open for time extensions",
                failure.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }
}
