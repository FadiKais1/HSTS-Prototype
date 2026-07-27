package hsts.server.repository;

import hsts.server.entity.ExamExecution;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionRepositoryEntityScheduleTest {
    private static final String LOCK_MARKER = "FROM exams e";
    private static final String INSERT_MARKER = "INSERT INTO exam_executions (";

    @Test
    public void schedulesEntityWithExactApprovedVersionAndAuthoritativeValues() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan lock = database.plan(LOCK_MARKER)
                .queryRows(row("exam_id", 40, "version_no", 3,
                        "duration_minutes", 75));
        ExamRepositoryJdbcTestSupport.StatementPlan insert = database.plan(INSERT_MARKER)
                .updateResults(1)
                .generatedKey(901);

        ExamExecution execution = new ExamExecutionRepository(database, () -> "A7Z9")
                .schedule(1002, 40, 3, openingTime(), closingTime());

        assertEquals(Map.of(1, 3, 2, 1002, 3, 40), lock.queryExecutions.get(0));
        String lockSql = normalized(lock.sql);
        assertTrue(lockSql.contains("EV.VERSION_NO = ?"));
        assertTrue(lockSql.contains("EV.STATUS = 'APPROVED'"));
        assertTrue(lockSql.contains("TC.TEACHER_USER_ID = ?"));
        assertTrue(lockSql.contains("MANAGER.ROLE IN ('TEACHER', 'COORDINATOR')"));
        assertTrue(lockSql.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(lockSql.contains("FOR UPDATE"));
        assertTrue(!lockSql.contains("CURRENT_VERSION_NO"));

        Map<Integer, Object> values = insert.updateExecutions.get(0);
        assertEquals("A7Z9", values.get(1));
        assertEquals(40, values.get(2));
        assertEquals(3, values.get(3));
        assertEquals(openingTime(), values.get(4));
        assertEquals(closingTime(), values.get(5));
        assertEquals(75, values.get(6));
        assertEquals("SCHEDULED", values.get(7));
        assertEquals(1002, values.get(8));
        assertTrue(values.get(9) instanceof LocalDateTime);
        assertEquals(values.get(9), values.get(16));

        assertEquals(901, execution.getExecutionId());
        assertEquals("A7Z9", execution.getExecutionCode());
        assertEquals(40, execution.getExamId());
        assertEquals(3, execution.getExamVersionNo());
        assertEquals(openingTime(), execution.getOpeningTime());
        assertEquals(closingTime(), execution.getClosingTime());
        assertEquals(75, execution.getDurationMinutes());
        assertEquals("SCHEDULED", execution.getStatus().name());
        assertEquals(1002, execution.getCreatedByUserId());
        assertEquals(values.get(9), execution.getCreatedAt());
        assertEquals(values.get(16), execution.getUpdatedAt());
        assertTrue(execution.getExecutionCode().matches("[A-Z0-9]{4}"));
        assertTrue(execution.getExamSubmissions().isEmpty());
        assertTrue(execution.getDecileDistribution().isEmpty());

        assertEquals(1, database.connectionRequests);
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.autoCommit);
        assertTrue(database.events.indexOf("query:" + LOCK_MARKER)
                < database.events.indexOf("update:" + INSERT_MARKER));
        assertTrue(database.events.indexOf("update:" + INSERT_MARKER)
                < database.events.indexOf("commit"));
    }

    @Test
    public void entityAndPayloadSchedulingShareParameterAndTransactionBehavior() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController entityDatabase =
                schedulingDatabase(901);
        ExamRepositoryJdbcTestSupport.FakeDatabaseController payloadDatabase =
                schedulingDatabase(902);

        new ExamExecutionRepository(entityDatabase, () -> "A7Z9")
                .schedule(1002, 40, 3, openingTime(), closingTime());
        new ExamExecutionRepository(payloadDatabase, () -> "A7Z9")
                .create(1002, new hsts.common.ScheduleExamExecutionPayload(
                        40, 3, openingTime(), closingTime()
                ));

        assertEquals(entityDatabase.plans.get(0).queryExecutions,
                payloadDatabase.plans.get(0).queryExecutions);
        Map<Integer, Object> entityValues = entityDatabase.plans.get(1)
                .updateExecutions.get(0);
        Map<Integer, Object> payloadValues = payloadDatabase.plans.get(1)
                .updateExecutions.get(0);
        for (int parameter = 1; parameter <= 8; parameter++) {
            assertEquals(entityValues.get(parameter), payloadValues.get(parameter));
        }
        for (int parameter = 10; parameter <= 15; parameter++) {
            assertEquals(entityValues.get(parameter), payloadValues.get(parameter));
        }
        assertEquals(entityDatabase.events, payloadDatabase.events);
        assertEquals(1, entityDatabase.commitCount);
        assertEquals(1, payloadDatabase.commitCount);
    }

    @Test
    public void entitySchedulingRetriesOnlyNamedCodeCollision() {
        SQLException collision = new SQLException(
                "Duplicate entry for key 'uq_exam_executions_code'",
                "23000",
                1062
        );
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(row("duration_minutes", 75));
        ExamRepositoryJdbcTestSupport.StatementPlan insert = database.plan(INSERT_MARKER)
                .updateFailure(collision)
                .updateResults(1)
                .generatedKey(903);
        Deque<String> codes = new ArrayDeque<>();
        codes.add("AAAA");
        codes.add("B2B2");

        ExamExecution execution = new ExamExecutionRepository(database, codes::removeFirst)
                .schedule(1003, 40, 3, openingTime(), closingTime());

        assertEquals(903, execution.getExecutionId());
        assertEquals("B2B2", execution.getExecutionCode());
        assertEquals(2, insert.updateExecutions.size());
        assertEquals("AAAA", insert.updateExecutions.get(0).get(1));
        assertEquals("B2B2", insert.updateExecutions.get(1).get(1));
        assertEquals(insert.updateExecutions.get(0).get(9),
                insert.updateExecutions.get(1).get(9));
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
    }

    @Test
    public void invalidEntityScheduleWindowFailsBeforeDatabaseAccess() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamExecutionRepository repository = new ExamExecutionRepository(database);

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> repository.schedule(1002, 40, 3, null, closingTime())
        );
        assertEquals("Opening and closing times are required", missing.getMessage());

        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> repository.schedule(1002, 40, 3, closingTime(), closingTime())
        );
        assertEquals("Execution closing time must be after opening time",
                invalid.getMessage());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void entitySchedulingFailureRollsBackRestoresAndPreservesSuppressedFailures() {
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
                () -> new ExamExecutionRepository(database, () -> "C3C3")
                        .schedule(1002, 40, 3, openingTime(), closingTime())
        );

        assertEquals("Failed to schedule exam execution", thrown.getMessage());
        assertSame(original, thrown.getCause());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertEquals(2, original.getSuppressed().length);
        assertSame(rollbackFailure, original.getSuppressed()[0]);
        assertSame(restorationFailure, original.getSuppressed()[1]);
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController schedulingDatabase(
            int generatedId
    ) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_MARKER).queryRows(row("duration_minutes", 75));
        database.plan(INSERT_MARKER).updateResults(1).generatedKey(generatedId);
        return database;
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
