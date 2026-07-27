package hsts.server.repository;

import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.type.ExecutionStatus;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionRepositoryReadTest {
    @Test
    public void managerListScopesCreatorActiveRoleAndCurrentAssignmentAndMapsRows() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan list = database.plan(
                "ORDER BY execution.created_at DESC, execution.execution_id DESC"
        ).queryRows(summaryRow(81, "A1B2"), summaryRow(77, "C3D4"));

        List<ExamExecutionSummaryDTO> executions = new ExamExecutionRepository(database)
                .findCreatedByManager(1002);

        assertEquals(2, executions.size());
        assertSummary(executions.get(0));
        assertEquals(81, executions.get(0).getExecutionId());
        assertEquals(77, executions.get(1).getExecutionId());
        assertEquals(Map.of(1, 1002, 2, 1002), list.queryExecutions.get(0));
        String sql = normalized(list.sql);
        assertTrue(sql.contains("MANAGER.ROLE IN ('TEACHER', 'COORDINATOR')"));
        assertTrue(sql.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(sql.contains("JOIN TEACHER_COURSES ASSIGNMENT"));
        assertTrue(sql.contains("ASSIGNMENT.COURSE_ID = EXAM.COURSE_ID"));
        assertTrue(sql.contains("WHERE EXECUTION.CREATED_BY_USER_ID = ?"));
        assertTrue(sql.contains("VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"));
        assertNoWrites(database);
    }

    @Test
    public void managerDetailRequiresCreatorRoleAndAssignmentAndReturnsEmptyWhenInaccessible() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController mappedDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan detail = mappedDatabase.plan(
                "AND execution.execution_id = ?"
        ).queryRows(summaryRow(81, "A1B2"));

        ExamExecutionSummaryDTO result = new ExamExecutionRepository(mappedDatabase)
                .findByIdForManager(1002, 81).orElseThrow();

        assertSummary(result);
        assertEquals(Map.of(1, 1002, 2, 1002, 3, 81),
                detail.queryExecutions.get(0));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController emptyDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        emptyDatabase.plan("AND execution.execution_id = ?").queryRows();
        assertTrue(new ExamExecutionRepository(emptyDatabase)
                .findByIdForManager(1002, 99).isEmpty());
        assertNoWrites(mappedDatabase);
        assertNoWrites(emptyDatabase);
    }

    @Test
    public void studentLookupScopesActiveEnrollmentAndOnlyAuthenticatedSubmission() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan lookup = database.plan(
                "WHERE execution.execution_code = ?"
        ).queryRows(previewRow(1));

        ExamExecutionPreviewDTO preview = new ExamExecutionRepository(database)
                .findByCodeForStudent(1001, "A1B2").orElseThrow();

        assertPreview(preview, true);
        assertEquals(Map.of(1, 1001, 2, 1001, 3, "A1B2"),
                lookup.queryExecutions.get(0));
        String sql = normalized(lookup.sql);
        assertTrue(sql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(sql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(sql.contains("JOIN STUDENT_COURSES ENROLLMENT"));
        assertTrue(sql.contains("ENROLLMENT.COURSE_ID = EXAM.COURSE_ID"));
        assertTrue(sql.contains("LEFT JOIN EXAM_SUBMISSIONS SUBMISSION"));
        assertTrue(sql.contains("SUBMISSION.STUDENT_USER_ID = ?"));
        assertTrue(sql.contains("SUBMISSION.STATUS = 'IN_PROGRESS'"));
        assertFalse(sql.contains("STUDENT.FULL_NAME"));
        assertFalse(sql.contains("STUDENT.EMAIL"));
        assertNoWrites(database);
    }

    @Test
    public void studentLookupMapsNonresumableAndReturnsEmptyForUnauthorizedOrInvalidCode() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan("WHERE execution.execution_code = ?")
                .queryRows(previewRow(0))
                .queryRows();
        ExamExecutionRepository repository = new ExamExecutionRepository(database);

        assertPreview(repository.findByCodeForStudent(1001, "A1B2").orElseThrow(), false);
        assertTrue(repository.findByCodeForStudent(1001, "Z9Z9").isEmpty());

        ExamRepositoryJdbcTestSupport.FakeDatabaseController invalidDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamExecutionRepository invalidRepository = new ExamExecutionRepository(
                invalidDatabase
        );
        assertTrue(invalidRepository.findByCodeForStudent(1001, null).isEmpty());
        assertTrue(invalidRepository.findByCodeForStudent(1001, "abc1").isEmpty());
        assertTrue(invalidRepository.findByCodeForStudent(1001, "ABCDE").isEmpty());
        assertEquals(0, invalidDatabase.connectionRequests);
    }

    @Test
    public void readFailuresUseExactWrappersAndPreserveOriginalCauses() {
        assertReadFailure(
                "Failed to load exam executions",
                repository -> repository.findCreatedByManager(1002)
        );
        assertReadFailure(
                "Failed to load exam execution",
                repository -> repository.findByIdForManager(1002, 81)
        );
        assertReadFailure(
                "Failed to validate execution code",
                repository -> repository.findByCodeForStudent(1001, "A1B2")
        );
    }

    private static void assertReadFailure(String message, RepositoryRead read) {
        SQLException failure = new SQLException("read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.connectionFailure = failure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> read.execute(new ExamExecutionRepository(database))
        );

        assertEquals(message, thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }

    private static Map<Object, Object> summaryRow(int executionId, String executionCode) {
        return row(
                "execution_id", executionId,
                "execution_code", executionCode,
                "exam_id", 40,
                "exam_version_no", 3,
                "exam_code", "EX1234",
                "exam_title", "Approved Midterm",
                "course_id", 7,
                "course_name", "Mathematics",
                "opening_time", LocalDateTime.of(2026, 8, 1, 9, 0),
                "closing_time", LocalDateTime.of(2026, 8, 1, 12, 0),
                "duration_minutes", 75,
                "status", "SCHEDULED",
                "created_by_user_id", 1002,
                "creator_name", "Development Teacher",
                "created_at", LocalDateTime.of(2026, 7, 28, 8, 30),
                "started_count", 4,
                "submitted_count", 2,
                "auto_submitted_count", 1
        );
    }

    private static Map<Object, Object> previewRow(int resumable) {
        return row(
                "execution_id", 81,
                "execution_code", "A1B2",
                "exam_id", 40,
                "exam_version_no", 3,
                "exam_title", "Approved Midterm",
                "course_id", 7,
                "course_name", "Mathematics",
                "opening_time", LocalDateTime.of(2026, 8, 1, 9, 0),
                "closing_time", LocalDateTime.of(2026, 8, 1, 12, 0),
                "duration_minutes", 75,
                "status", "OPEN",
                "resumable", resumable
        );
    }

    private static void assertSummary(ExamExecutionSummaryDTO summary) {
        assertEquals("A1B2", summary.getExecutionCode());
        assertEquals(40, summary.getExamId());
        assertEquals(3, summary.getExamVersionNo());
        assertEquals("EX1234", summary.getExamCode());
        assertEquals("Approved Midterm", summary.getExamTitle());
        assertEquals(7, summary.getCourseId());
        assertEquals("Mathematics", summary.getCourseName());
        assertEquals(LocalDateTime.of(2026, 8, 1, 9, 0), summary.getOpeningTime());
        assertEquals(LocalDateTime.of(2026, 8, 1, 12, 0), summary.getClosingTime());
        assertEquals(75, summary.getDurationMinutes());
        assertEquals(ExecutionStatus.SCHEDULED, summary.getStatus());
        assertEquals(1002, summary.getCreatedByUserId());
        assertEquals("Development Teacher", summary.getCreatorName());
        assertEquals(LocalDateTime.of(2026, 7, 28, 8, 30), summary.getCreatedAt());
        assertEquals(Integer.valueOf(4), summary.getStartedCount());
        assertEquals(Integer.valueOf(2), summary.getSubmittedCount());
        assertEquals(Integer.valueOf(1), summary.getAutoSubmittedCount());
    }

    private static void assertPreview(ExamExecutionPreviewDTO preview,
                                      boolean resumable) {
        assertEquals(81, preview.getExecutionId());
        assertEquals("A1B2", preview.getExecutionCode());
        assertEquals(40, preview.getExamId());
        assertEquals(3, preview.getExamVersionNo());
        assertEquals("Approved Midterm", preview.getExamTitle());
        assertEquals(7, preview.getCourseId());
        assertEquals("Mathematics", preview.getCourseName());
        assertEquals(LocalDateTime.of(2026, 8, 1, 9, 0), preview.getOpeningTime());
        assertEquals(LocalDateTime.of(2026, 8, 1, 12, 0), preview.getClosingTime());
        assertEquals(75, preview.getDurationMinutes());
        assertEquals(ExecutionStatus.OPEN, preview.getStatus());
        assertEquals(resumable, preview.isResumable());
    }

    private static void assertNoWrites(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database
    ) {
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
        assertTrue(database.events.stream().noneMatch(event -> event.startsWith("update:")));
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }

    @FunctionalInterface
    private interface RepositoryRead {
        void execute(ExamExecutionRepository repository);
    }
}
