package hsts.server.repository;

import hsts.common.type.ExecutionStatus;
import hsts.server.entity.ExamExecution;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionRepositoryEntityReadTest {
    private static final String MANAGER_MARKER =
            "WHERE execution.created_by_user_id = ?";
    private static final String STUDENT_MARKER = "JOIN users student";
    private static final String STUDENT_ID_MARKER =
            "WHERE execution.execution_id = ?";
    private static final String DECILES_MARKER = "FROM exam_execution_deciles";

    @Test
    public void managerEntityReadScopesAccessAndHydratesCompletePersistedState() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan entityQuery = database.plan(
                MANAGER_MARKER
        ).queryRows(closedEntityRow());
        ExamRepositoryJdbcTestSupport.StatementPlan deciles = database.plan(
                DECILES_MARKER
        ).queryRows(
                decile(1, 0), decile(2, 0), decile(3, 1), decile(4, 0),
                decile(5, 0), decile(6, 1), decile(7, 0), decile(8, 1),
                decile(9, 0), decile(10, 1)
        );

        ExamExecution execution = new ExamExecutionRepository(database)
                .findEntityForManager(1002, 81)
                .orElseThrow();

        assertEquals(Map.of(1, 1002, 2, 1002, 3, 81),
                entityQuery.queryExecutions.get(0));
        assertEquals(Map.of(1, 81), deciles.queryExecutions.get(0));
        assertEquals(81, execution.getExecutionId());
        assertEquals("A1B2", execution.getExecutionCode());
        assertEquals(40, execution.getExamId());
        assertEquals(3, execution.getExamVersionNo());
        assertEquals(openingTime(), execution.getOpeningTime());
        assertEquals(closingTime(), execution.getClosingTime());
        assertEquals(75, execution.getDurationMinutes());
        assertEquals(ExecutionStatus.CLOSED, execution.getStatus());
        assertEquals(1002, execution.getCreatedByUserId());
        assertEquals(createdAt(), execution.getCreatedAt());
        assertEquals(updatedAt(), execution.getUpdatedAt());
        assertEquals(closedAt(), execution.getClosedAt());
        assertEquals(new BigDecimal("82.50"), execution.getAverageScoreValue());
        assertEquals(new BigDecimal("80.00"), execution.getMedianScoreValue());
        assertEquals(4, execution.getStartedCount());
        assertEquals(3, execution.getSubmittedCount());
        assertEquals(1, execution.getAutoSubmittedCount());
        assertEquals(List.of(0, 0, 1, 0, 0, 1, 0, 1, 0, 1),
                execution.getDecileDistribution());
        assertTrue(execution.getExamSubmissions().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> execution.getDecileDistribution().add(1));

        String sql = normalized(entityQuery.sql);
        assertTrue(sql.contains("VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"));
        assertTrue(sql.contains("MANAGER.ROLE IN ('TEACHER', 'COORDINATOR')"));
        assertTrue(sql.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(sql.contains("JOIN TEACHER_COURSES ASSIGNMENT"));
        assertTrue(sql.contains("ASSIGNMENT.COURSE_ID = EXAM.COURSE_ID"));
        assertTrue(sql.contains("EXECUTION.UPDATED_AT"));
        assertFalse(sql.contains("CURRENT_VERSION_NO"));
        assertFalse(sql.contains("EXAM_SUBMISSIONS"));
    }

    @Test
    public void managerEntityReadReturnsEmptyWhenMissingOrUnauthorized() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(MANAGER_MARKER).queryRows();

        assertTrue(new ExamExecutionRepository(database)
                .findEntityForManager(1002, 99)
                .isEmpty());
        assertEquals(1, database.plans.size());
    }

    @Test
    public void studentEntityReadNormalizesCodeAndPreservesEnrollmentScope() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        Map<Object, Object> row = openEntityRow();
        ExamRepositoryJdbcTestSupport.StatementPlan entityQuery = database.plan(
                STUDENT_MARKER
        ).queryRows(row);
        database.plan(DECILES_MARKER).queryRows();

        ExamExecution execution = new ExamExecutionRepository(database)
                .findEntityByCodeForStudent(1001, "  a1b2  ")
                .orElseThrow();

        assertEquals(Map.of(1, 1001, 2, "A1B2"),
                entityQuery.queryExecutions.get(0));
        assertEquals(ExecutionStatus.OPEN, execution.getStatus());
        assertEquals(row.get("updated_at"), execution.getUpdatedAt());
        assertTrue(execution.getExamSubmissions().isEmpty());
        assertTrue(execution.getDecileDistribution().isEmpty());

        String sql = normalized(entityQuery.sql);
        assertTrue(sql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(sql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(sql.contains("JOIN STUDENT_COURSES ENROLLMENT"));
        assertTrue(sql.contains("ENROLLMENT.COURSE_ID = EXAM.COURSE_ID"));
        assertTrue(sql.contains("VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"));
        assertFalse(sql.contains("CURRENT_VERSION_NO"));
        assertFalse(sql.contains("STUDENT_PROFILES"));
        assertFalse(sql.contains("IDENTITY_NUMBER_HASH"));
        assertFalse(sql.contains("EXAM_SUBMISSIONS"));
        assertFalse(sql.contains("STUDENT_ANSWERS"));
        assertFalse(sql.contains("QUESTION_VERSIONS"));
        assertFalse(sql.contains("ANSWER_OPTIONS"));
    }

    @Test
    public void studentEntityReadRejectsInvalidCodesWithoutDatabaseAccess() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamExecutionRepository repository = new ExamExecutionRepository(database);

        assertTrue(repository.findEntityByCodeForStudent(1001, null).isEmpty());
        assertTrue(repository.findEntityByCodeForStudent(1001, "  ").isEmpty());
        assertTrue(repository.findEntityByCodeForStudent(1001, "AB-1").isEmpty());
        assertTrue(repository.findEntityByCodeForStudent(1001, "ABCDE").isEmpty());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void studentEntityReadByIdScopesEnrollmentAndHydratesAuthoritativeState() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        Map<Object, Object> row = closedEntityRow();
        ExamRepositoryJdbcTestSupport.StatementPlan entityQuery = database.plan(
                STUDENT_ID_MARKER
        ).queryRows(row);
        ExamRepositoryJdbcTestSupport.StatementPlan deciles = database.plan(
                DECILES_MARKER
        ).queryRows(
                decile(1, 0), decile(2, 0), decile(3, 1), decile(4, 0),
                decile(5, 0), decile(6, 1), decile(7, 0), decile(8, 1),
                decile(9, 0), decile(10, 1)
        );

        ExamExecution execution = new ExamExecutionRepository(database)
                .findEntityForStudent(1001, 81)
                .orElseThrow();

        assertEquals(Map.of(1, 1001, 2, 81), entityQuery.queryExecutions.get(0));
        assertEquals(Map.of(1, 81), deciles.queryExecutions.get(0));
        assertEquals(81, execution.getExecutionId());
        assertEquals("A1B2", execution.getExecutionCode());
        assertEquals(40, execution.getExamId());
        assertEquals(3, execution.getExamVersionNo());
        assertEquals(openingTime(), execution.getOpeningTime());
        assertEquals(closingTime(), execution.getClosingTime());
        assertEquals(75, execution.getDurationMinutes());
        assertEquals(ExecutionStatus.CLOSED, execution.getStatus());
        assertEquals(1002, execution.getCreatedByUserId());
        assertEquals(createdAt(), execution.getCreatedAt());
        assertEquals(updatedAt(), execution.getUpdatedAt());
        assertEquals(closedAt(), execution.getClosedAt());
        assertEquals(new BigDecimal("82.50"), execution.getAverageScoreValue());
        assertEquals(new BigDecimal("80.00"), execution.getMedianScoreValue());
        assertEquals(4, execution.getStartedCount());
        assertEquals(3, execution.getSubmittedCount());
        assertEquals(1, execution.getAutoSubmittedCount());
        assertEquals(List.of(0, 0, 1, 0, 0, 1, 0, 1, 0, 1),
                execution.getDecileDistribution());
        assertTrue(execution.getExamSubmissions().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> execution.getExamSubmissions().add(null));

        String sql = normalized(entityQuery.sql);
        assertTrue(sql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(sql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(sql.contains("JOIN STUDENT_COURSES ENROLLMENT"));
        assertTrue(sql.contains("ENROLLMENT.STUDENT_USER_ID = STUDENT.USER_ID"));
        assertTrue(sql.contains("ENROLLMENT.COURSE_ID = EXAM.COURSE_ID"));
        assertTrue(sql.contains("WHERE EXECUTION.EXECUTION_ID = ?"));
        assertTrue(sql.contains("VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"));
        assertTrue(sql.contains("EXECUTION.UPDATED_AT"));
        assertFalse(sql.contains("CURRENT_VERSION_NO"));
        assertFalse(sql.contains("EXECUTION.EXECUTION_CODE = ?"));
        assertFalse(sql.contains("STUDENT_PROFILES"));
        assertFalse(sql.contains("IDENTITY_NUMBER_HASH"));
        assertFalse(sql.contains("EXAM_SUBMISSIONS"));
        assertFalse(sql.contains("STUDENT_ANSWERS"));
        assertFalse(sql.contains("QUESTION_VERSIONS"));
        assertFalse(sql.contains("ANSWER_OPTIONS"));
        assertFalse(sql.contains("PASSWORD"));
    }

    @Test
    public void studentEntityReadByIdReturnsEmptyWhenMissingOrUnauthorized() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan entityQuery = database.plan(
                STUDENT_ID_MARKER
        ).queryRows().queryRows();
        ExamExecutionRepository repository = new ExamExecutionRepository(database);

        assertTrue(repository.findEntityForStudent(1001, 99).isEmpty());
        assertTrue(repository.findEntityForStudent(1004, 81).isEmpty());
        assertEquals(Map.of(1, 1001, 2, 99), entityQuery.queryExecutions.get(0));
        assertEquals(Map.of(1, 1004, 2, 81), entityQuery.queryExecutions.get(1));
        assertEquals(1, database.plans.size());
    }

    @Test
    public void studentEntityReadByIdRejectsMalformedStateAndPreservesJdbcCause() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController malformedDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        Map<Object, Object> malformed = openEntityRow();
        malformed.put("status", "UNKNOWN");
        malformedDatabase.plan(STUDENT_ID_MARKER).queryRows(malformed);

        IllegalArgumentException malformedFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamExecutionRepository(malformedDatabase)
                        .findEntityForStudent(1001, 81)
        );
        assertEquals("Execution status is invalid: 81", malformedFailure.getMessage());

        SQLException cause = new SQLException("student execution read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController failedDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        failedDatabase.connectionFailure = cause;

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ExamExecutionRepository(failedDatabase)
                        .findEntityForStudent(1001, 81)
        );
        assertEquals("Failed to load student exam execution entity",
                failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    public void malformedPersistedExecutionStateIsRejectedDeterministically() {
        assertMalformed("execution_code", "bad", "four uppercase alphanumeric");
        assertMalformed("duration_minutes", 0, "duration must be positive");
        assertMalformed("closing_time", openingTime(), "must be after opening time");
        assertMalformed("status", "UNKNOWN", "Execution status is invalid: 81");
        assertMalformed("updated_at", createdAt().minusNanos(1),
                "cannot precede creation");
        assertMalformed("started_count", -1, "counts cannot be negative");

        Map<Object, Object> inconsistentCounts = closedEntityRow();
        inconsistentCounts.put("started_count", 2);
        assertMalformed(inconsistentCounts, "cannot exceed starts");
    }

    @Test
    public void partialOrMisorderedDecilesAreRejected() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(MANAGER_MARKER).queryRows(closedEntityRow());
        database.plan(DECILES_MARKER).queryRows(decile(2, 1));

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamExecutionRepository(database)
                        .findEntityForManager(1002, 81)
        );

        assertEquals("Execution decile distribution is invalid: 81", thrown.getMessage());
    }

    @Test
    public void entityReadFailuresPreserveJdbcCauseWithoutLeakingCode() {
        SQLException managerFailure = new SQLException("manager read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController managerDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        managerDatabase.connectionFailure = managerFailure;

        IllegalStateException managerThrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamExecutionRepository(managerDatabase)
                        .findEntityForManager(1002, 81)
        );
        assertEquals("Failed to load manager exam execution entity",
                managerThrown.getMessage());
        assertSame(managerFailure, managerThrown.getCause());

        SQLException studentFailure = new SQLException("student read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController studentDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        studentDatabase.connectionFailure = studentFailure;

        IllegalStateException studentThrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamExecutionRepository(studentDatabase)
                        .findEntityByCodeForStudent(1001, "A1B2")
        );
        assertEquals("Failed to load student exam execution entity",
                studentThrown.getMessage());
        assertFalse(studentThrown.getMessage().contains("A1B2"));
        assertSame(studentFailure, studentThrown.getCause());
    }

    private static void assertMalformed(Object key, Object value, String messagePart) {
        Map<Object, Object> malformed = closedEntityRow();
        malformed.put(key, value);
        assertMalformed(malformed, messagePart);
    }

    private static void assertMalformed(Map<Object, Object> malformed,
                                        String messagePart) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(MANAGER_MARKER).queryRows(malformed);
        database.plan(DECILES_MARKER).queryRows();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamExecutionRepository(database)
                        .findEntityForManager(1002, 81)
        );
        assertTrue(thrown.getMessage().contains(messagePart));
    }

    private static Map<Object, Object> closedEntityRow() {
        return row(
                "execution_id", 81,
                "execution_code", "A1B2",
                "exam_id", 40,
                "exam_version_no", 3,
                "opening_time", openingTime(),
                "closing_time", closingTime(),
                "duration_minutes", 75,
                "status", "CLOSED",
                "created_by_user_id", 1002,
                "created_at", createdAt(),
                "updated_at", updatedAt(),
                "closed_at", closedAt(),
                "average_score", new BigDecimal("82.50"),
                "median_score", new BigDecimal("80.00"),
                "started_count", 4,
                "submitted_count", 3,
                "auto_submitted_count", 1
        );
    }

    private static Map<Object, Object> openEntityRow() {
        Map<Object, Object> row = closedEntityRow();
        row.put("status", "OPEN");
        row.put("updated_at", LocalDateTime.of(2026, 8, 1, 9, 30));
        row.put("closed_at", null);
        row.put("average_score", null);
        row.put("median_score", null);
        row.put("started_count", 1);
        row.put("submitted_count", 0);
        row.put("auto_submitted_count", 0);
        return row;
    }

    private static Map<Object, Object> decile(int number, int count) {
        return row("decile_number", number, "submission_count", count);
    }

    private static LocalDateTime openingTime() {
        return LocalDateTime.of(2026, 8, 1, 9, 0);
    }

    private static LocalDateTime closingTime() {
        return LocalDateTime.of(2026, 8, 1, 12, 0);
    }

    private static LocalDateTime createdAt() {
        return LocalDateTime.of(2026, 7, 28, 8, 30);
    }

    private static LocalDateTime updatedAt() {
        return LocalDateTime.of(2026, 8, 1, 12, 5);
    }

    private static LocalDateTime closedAt() {
        return LocalDateTime.of(2026, 8, 1, 12, 5);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
