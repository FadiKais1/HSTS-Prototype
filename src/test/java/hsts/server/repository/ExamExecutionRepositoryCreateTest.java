package hsts.server.repository;

import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionRepositoryCreateTest {
    private static final String LOCK_MARKER = "FROM exams e";
    private static final String INSERT_MARKER = "INSERT INTO exam_executions (";
    private static final String RECIPIENTS_MARKER = "FROM student_courses enrollment";
    private static final String NOTIFICATION_MARKER = "INSERT INTO notifications";

    @Test
    public void missingScheduleTimesFailBeforeObtainingConnection() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamExecutionRepository(database).schedule(
                        1002, 40, 3, null, closingTime()
                )
        );

        assertEquals("Opening and closing times are required", thrown.getMessage());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void schedulesApprovedImmutableVersionWithInheritedDurationAtomically() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan lock = database.plan(LOCK_MARKER)
                .queryRows(approvedVersionRow());
        ExamRepositoryJdbcTestSupport.StatementPlan insert = database.plan(INSERT_MARKER)
                .updateResults(1).generatedKey(901);
        database.plan(RECIPIENTS_MARKER).queryRows();

        int executionId = schedule(
                new ExamExecutionRepository(database, () -> "A7Z9"), 1002
        );

        assertEquals(901, executionId);
        assertEquals(1, database.connectionRequests);
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.autoCommit);
        assertEquals(2, database.autoCommitSetCalls);
        assertEquals(Map.of(1, 3, 2, 1002, 3, 40), lock.queryExecutions.get(0));

        String lockSql = normalized(lock.sql);
        assertTrue(lockSql.contains("JOIN EXAM_VERSIONS EV"));
        assertTrue(lockSql.contains("EV.VERSION_NO = ?"));
        assertTrue(lockSql.contains("EV.STATUS = 'APPROVED'"));
        assertTrue(lockSql.contains("JOIN TEACHER_COURSES TC"));
        assertTrue(lockSql.contains("TC.TEACHER_USER_ID = ?"));
        assertTrue(lockSql.contains("MANAGER.ROLE IN ('TEACHER', 'COORDINATOR')"));
        assertTrue(lockSql.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(lockSql.contains("FOR UPDATE"));

        Map<Integer, Object> values = insert.updateExecutions.get(0);
        assertEquals("A7Z9", values.get(1));
        assertTrue(values.get(1).toString().matches("[A-Z0-9]{4}"));
        assertEquals(40, values.get(2));
        assertEquals(3, values.get(3));
        assertEquals(openingTime(), values.get(4));
        assertEquals(closingTime(), values.get(5));
        assertEquals(75, values.get(6));
        assertEquals("SCHEDULED", values.get(7));
        assertEquals(1002, values.get(8));
        assertTrue(values.get(9) instanceof LocalDateTime);
        assertEquals(null, values.get(10));
        assertEquals(null, values.get(11));
        assertEquals(null, values.get(12));
        assertEquals(0, values.get(13));
        assertEquals(0, values.get(14));
        assertEquals(0, values.get(15));
        assertTrue(database.events.indexOf("query:" + LOCK_MARKER)
                < database.events.indexOf("update:" + INSERT_MARKER));
        assertTrue(database.events.indexOf("update:" + INSERT_MARKER)
                < database.events.indexOf("commit"));
    }

    @Test
    public void inaccessibleOrUnapprovedVersionIsUnwrappedDomainFailureAndRollsBack() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> schedule(new ExamExecutionRepository(database), 1002)
        );

        assertEquals("Exam version is not approved or accessible", thrown.getMessage());
        assertEquals(null, thrown.getCause());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    @Test
    public void retriesOnlyNamedExecutionCodeCollisionAndUsesOneConnection() {
        SQLException collision = new SQLException(
                "Duplicate entry for key 'uq_exam_executions_code'",
                "23000",
                1062
        );
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER)
                .queryRows(approvedVersionRow());
        ExamRepositoryJdbcTestSupport.StatementPlan insert = database.plan(INSERT_MARKER)
                .updateFailure(collision).updateResults(1).generatedKey(902);
        database.plan(RECIPIENTS_MARKER).queryRows();
        Deque<String> codes = new ArrayDeque<>();
        codes.add("AAAA");
        codes.add("B2B2");

        int result = schedule(
                new ExamExecutionRepository(database, codes::removeFirst), 1003
        );

        assertEquals(902, result);
        assertEquals(1, database.connectionRequests);
        assertEquals(2, insert.updateExecutions.size());
        assertEquals("AAAA", insert.updateExecutions.get(0).get(1));
        assertEquals("B2B2", insert.updateExecutions.get(1).get(1));
        assertEquals(insert.updateExecutions.get(0).get(9),
                insert.updateExecutions.get(1).get(9));
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
    }

    @Test
    public void unrelatedSqlFailureIsNotRetriedAndPreservesCause() {
        SQLException failure = new SQLException("write failed", "42000", 1064);
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(row("duration_minutes", 75));
        ExamRepositoryJdbcTestSupport.StatementPlan insert = database.plan(INSERT_MARKER)
                .updateFailure(failure);
        AtomicInteger generatedCodes = new AtomicInteger();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> schedule(new ExamExecutionRepository(database, () -> {
                    generatedCodes.incrementAndGet();
                    return "C3C3";
                }), 1002)
        );

        assertEquals("Failed to schedule exam execution", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, generatedCodes.get());
        assertEquals(1, insert.updateExecutions.size());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }

    @Test
    public void executionCodeCollisionRetriesAreBounded() {
        SQLException collision = new SQLException(
                "Duplicate entry for key 'uq_exam_executions_code'",
                "23000",
                1062
        );
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(row("duration_minutes", 75));
        ExamRepositoryJdbcTestSupport.StatementPlan insert = database.plan(INSERT_MARKER);
        for (int attempt = 0; attempt < 5; attempt++) {
            insert.updateFailure(collision);
        }
        AtomicInteger generatedCodes = new AtomicInteger();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> schedule(new ExamExecutionRepository(database, () -> {
                    generatedCodes.incrementAndGet();
                    return "F6F6";
                }), 1002)
        );

        assertEquals("Failed to schedule exam execution", thrown.getMessage());
        assertSame(collision, thrown.getCause());
        assertEquals(5, generatedCodes.get());
        assertEquals(5, insert.updateExecutions.size());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }

    @Test
    public void missingOrInvalidGeneratedKeyRollsBack() {
        assertGeneratedKeyFailure(false);
        assertGeneratedKeyFailure(true);
    }

    @Test
    public void schedulingNotifiesOnlyActiveEnrolledStudentsInsideTheTransaction() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(approvedVersionRow());
        database.plan(INSERT_MARKER).updateResults(1).generatedKey(903);
        ExamRepositoryJdbcTestSupport.StatementPlan recipients =
                database.plan(RECIPIENTS_MARKER).queryRows(
                        row("user_id", 1001),
                        row("user_id", 1005)
                );
        ExamRepositoryJdbcTestSupport.StatementPlan notifications =
                database.plan(NOTIFICATION_MARKER).updateResults(1, 1);

        int executionId = schedule(
                new ExamExecutionRepository(database, () -> "Q7W9"), 1002
        );

        assertEquals(903, executionId);
        String recipientSql = normalized(recipients.sql);
        assertTrue(recipientSql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(recipientSql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(recipientSql.contains("ENROLLMENT.COURSE_ID = ?"));
        assertEquals(Map.of(1, 7), recipients.queryExecutions.get(0));
        assertEquals(2, notifications.updateExecutions.size());
        for (int index = 0; index < 2; index++) {
            Map<Integer, Object> values = notifications.updateExecutions.get(index);
            assertEquals(index == 0 ? 1001 : 1005, values.get(1));
            assertEquals("EXAM_SCHEDULED", values.get(2));
            assertEquals("Exam scheduled", values.get(3));
            assertTrue(values.get(4).toString().contains("Algebra Midterm"));
            assertTrue(values.get(4).toString().contains("Legacy Course"));
            assertTrue(values.get(4).toString().contains("Q7W9"));
            assertTrue(values.get(4).toString().contains("75 minutes"));
            assertEquals(40, values.get(5));
            assertEquals(903, values.get(6));
        }
        assertTrue(database.events.indexOf("update:" + NOTIFICATION_MARKER)
                < database.events.indexOf("commit"));
    }

    @Test
    public void schedulingNotificationFailureRollsBackExecutionAndNotifications() {
        SQLException failure = new SQLException("notification insert failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(approvedVersionRow());
        database.plan(INSERT_MARKER).updateResults(1).generatedKey(904);
        database.plan(RECIPIENTS_MARKER).queryRows(row("user_id", 1001));
        database.plan(NOTIFICATION_MARKER).updateFailure(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> schedule(
                        new ExamExecutionRepository(database, () -> "R8X0"), 1002
                )
        );

        assertEquals("Failed to schedule exam execution", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
    }

    @Test
    public void rollbackAndRestorationFailuresAreSuppressedOnOriginalJdbcCause() {
        SQLException original = new SQLException("insert failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(row("duration_minutes", 75));
        database.plan(INSERT_MARKER).updateFailure(original);
        database.rollbackFailure = rollbackFailure;
        database.restorationFailure = restorationFailure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> schedule(
                        new ExamExecutionRepository(database, () -> "D4D4"), 1002
                )
        );

        assertSame(original, thrown.getCause());
        assertEquals(2, original.getSuppressed().length);
        assertSame(rollbackFailure, original.getSuppressed()[0]);
        assertSame(restorationFailure, original.getSuppressed()[1]);
    }

    private static void assertGeneratedKeyFailure(boolean invalidKey) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController(false);
        database.plan(LOCK_MARKER).queryRows(row("duration_minutes", 75));
        ExamRepositoryJdbcTestSupport.StatementPlan insert = database.plan(INSERT_MARKER)
                .updateResults(1);
        if (invalidKey) {
            insert.generatedKey(0);
        } else {
            insert.noGeneratedKey();
        }

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> schedule(
                        new ExamExecutionRepository(database, () -> "E5E5"), 1002
                )
        );

        assertEquals("Failed to schedule exam execution", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof SQLException);
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertFalse(database.autoCommit);
    }

    private static int schedule(ExamExecutionRepository repository, int userId) {
        return repository.schedule(userId, 40, 3, openingTime(), closingTime())
                .getExecutionId();
    }

    private static Map<Object, Object> approvedVersionRow() {
        return row(
                "exam_id", 40,
                "version_no", 3,
                "duration_minutes", 75,
                "exam_title", "Algebra Midterm",
                "course_id", 7,
                "course_name", "Legacy Course"
        );
    }

    private static LocalDateTime openingTime() {
        return LocalDateTime.of(2026, 8, 1, 9, 0);
    }

    private static LocalDateTime closingTime() {
        return LocalDateTime.of(2026, 8, 1, 12, 0);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
