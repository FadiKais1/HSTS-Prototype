package hsts.server.repository;

import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;
import hsts.server.entity.ExamExecution;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.StudentAnswer;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.executionRow;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryEntityPersistenceTest {
    private static final String EXECUTION_MARKER =
            "WHERE execution.execution_id = ?";
    private static final String EXISTING_MARKER =
            "WHERE submission.execution_id = ?";
    private static final String INSERT_MARKER = "INSERT INTO exam_submissions (";
    private static final String STARTED_COUNT_MARKER =
            "SET started_count = started_count + 1";
    private static final String LOCK_STUDENT_MARKER =
            "AND submission.student_user_id = ? FOR UPDATE";
    private static final String LOCK_INTERNAL_MARKER =
            "WHERE submission.submission_id = ? FOR UPDATE";
    private static final String QUESTION_MARKER =
            "WHERE selection.exam_id = ?";
    private static final String UPSERT_MARKER = "ON DUPLICATE KEY UPDATE";
    private static final String INTERNAL_ENTITY_MARKER =
            "WHERE submission.submission_id = ?";
    private static final String ANSWERS_MARKER =
            "ORDER BY selection.order_number ASC, answer.answer_id ASC";
    private static final String ENTITY_ANSWER_GRADE_MARKER =
            "AND question_version_no = ?";
    private static final String ENTITY_FINALIZE_MARKER =
            "updated_at = ? WHERE submission_id = ?";
    private static final String SUBMITTED_COUNT_MARKER =
            "SET submitted_count = submitted_count + 1";
    private static final String AUTO_COUNT_MARKER =
            "SET auto_submitted_count = auto_submitted_count + 1";
    private static final String MANAGER_EXTENSION_MARKER =
            "JOIN users manager";
    private static final String ENTITY_EXTENSION_MARKER =
            "AND extra_minutes = ?";
    private static final String AUDIT_MARKER =
            "INSERT INTO submission_time_extensions";

    @Test
    public void entityStartUsesSuppliedExactExecutionAndReturnsHydratedState() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(EXECUTION_MARKER).queryRows(executionRow("OPEN"));
        database.plan(EXISTING_MARKER).queryRows();
        database.plan(INSERT_MARKER).updateResults(1).generatedKey(501);
        database.plan(STARTED_COUNT_MARKER).updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan reread = database.plan(
                INTERNAL_ENTITY_MARKER
        ).queryRows(entityRow(SubmissionStatus.IN_PROGRESS, 0, null));
        database.plan(ANSWERS_MARKER).queryRows();
        ExamExecution supplied = executionEntity();

        ExamSubmission result = new ExamSubmissionRepository(database)
                .startOrResume(1001, supplied, STARTED);

        assertEquals(501, result.getSubmissionId());
        assertEquals(81, result.getExecutionId());
        assertEquals(40, result.getExamId());
        assertEquals(3, result.getExamVersionNo());
        assertEquals(1001, result.getStudentUserId());
        assertEquals(STARTED.plusMinutes(75), result.getEffectiveDeadline());
        assertTrue(result.getStudentAnswers().isEmpty());
        assertEquals(Map.of(1, 501), reread.queryExecutions.get(0));
        assertEquals(ExecutionStatus.OPEN, supplied.getStatus());
        assertTrue(supplied.getExamSubmissions().isEmpty());
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
    }

    @Test
    public void entityStartRejectsMismatchedExecutionBeforeWriting() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(EXECUTION_MARKER).queryRows(executionRow("OPEN"));
        ExamExecution mismatched = ExamExecution.rehydrate(
                81, "A1B2", 40, 4, OPENING, CLOSING, 75,
                ExecutionStatus.OPEN, 1002, CREATED, null, null, null,
                List.of(), 0, 0, 0, OPENING, List.of()
        );

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database)
                        .startOrResume(1001, mismatched, STARTED)
        );

        assertEquals("Execution state does not match supplied entity",
                thrown.getMessage());
        assertEquals(1, database.rollbackCount);
        assertTrue(database.events.stream().noneMatch(event ->
                event.startsWith("update:")));
    }

    @Test
    public void entityAnswerSavePersistsNullGradingAndRereadsExamOrder() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_STUDENT_MARKER).queryRows(lockRow());
        database.plan(QUESTION_MARKER).queryRows(row("question_version_no", 7));
        ExamRepositoryJdbcTestSupport.StatementPlan upsert = database.plan(
                UPSERT_MARKER
        ).updateResults(1);
        database.plan(INTERNAL_ENTITY_MARKER).queryRows(
                entityRow(SubmissionStatus.IN_PROGRESS, 0, null)
        );
        database.plan(ANSWERS_MARKER).queryRows(
                answerRow(901, 30, 7, 1, 4, null, null, CURRENT),
                answerRow(902, 10, 2, 2, 2, null, null, CURRENT.minusMinutes(1))
        );
        ExamSubmission supplied = inProgressSubmission();
        StudentAnswer answer = StudentAnswer.select(501, 30, 7, 4, CURRENT);

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistAnswer(1001, supplied, answer, CURRENT);

        Map<Integer, Object> values = upsert.updateExecutions.get(0);
        assertEquals(501, values.get(1));
        assertEquals(30, values.get(2));
        assertEquals(7, values.get(3));
        assertEquals(4, values.get(4));
        assertEquals(null, values.get(6));
        assertEquals(null, values.get(7));
        assertEquals(CURRENT, values.get(8));
        assertEquals(CURRENT, values.get(9));
        assertEquals(List.of(30, 10), result.getStudentAnswers().stream()
                .map(StudentAnswer::getQuestionId).toList());
        assertTrue(result.getStudentAnswers().stream()
                .noneMatch(StudentAnswer::isGraded));
        assertTrue(supplied.getStudentAnswers().isEmpty());
        assertFalse(answer.isGraded());
    }

    @Test
    public void manualEntityFinalizationPersistsPrecomputedGradesAtomically() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_STUDENT_MARKER).queryRows(lockRow());
        database.plan(QUESTION_MARKER).queryRows(row("question_version_no", 7));
        ExamRepositoryJdbcTestSupport.StatementPlan answerUpdate = database.plan(
                ENTITY_ANSWER_GRADE_MARKER
        ).updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan finalizeUpdate = database.plan(
                ENTITY_FINALIZE_MARKER
        ).updateResults(1);
        database.plan(SUBMITTED_COUNT_MARKER).updateResults(1);
        database.plan(INTERNAL_ENTITY_MARKER).queryRows(
                entityRow(SubmissionStatus.SUBMITTED, 30, new BigDecimal("40.00"))
        );
        database.plan(ANSWERS_MARKER).queryRows(
                answerRow(901, 30, 7, 1, 2, true,
                        new BigDecimal("40.00"), GRADED)
        );
        ExamSubmission finalized = gradedManualSubmission();

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistStudentSubmission(1001, finalized);

        Map<Integer, Object> answerValues = answerUpdate.updateExecutions.get(0);
        assertEquals(true, answerValues.get(1));
        assertEquals(new BigDecimal("40.00"), answerValues.get(2));
        assertEquals(GRADED, answerValues.get(3));
        assertEquals(901, answerValues.get(4));
        assertEquals(501, answerValues.get(5));
        assertEquals(30, answerValues.get(6));
        assertEquals(7, answerValues.get(7));

        Map<Integer, Object> finalValues = finalizeUpdate.updateExecutions.get(0);
        assertEquals(SUBMITTED, finalValues.get(1));
        assertEquals("SUBMITTED", finalValues.get(2));
        assertEquals(30, finalValues.get(3));
        assertEquals(new BigDecimal("40.00"), finalValues.get(4));
        assertEquals(new BigDecimal("40.00"), finalValues.get(5));
        assertEquals(GRADED, finalValues.get(6));
        assertEquals(SubmissionStatus.SUBMITTED, result.getStatus());
        assertEquals(new BigDecimal("40.00"),
                result.getAutomaticScoreValue().orElseThrow());
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.events.stream().noneMatch(event ->
                event.contains("correct_option")));
    }

    @Test
    public void automaticEntityFinalizationRequiresExpiredLockedSource() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_INTERNAL_MARKER).queryRows(lockRow());
        database.plan(ENTITY_FINALIZE_MARKER).updateResults(1);
        database.plan(AUTO_COUNT_MARKER).updateResults(1);
        database.plan(INTERNAL_ENTITY_MARKER).queryRows(
                entityRow(SubmissionStatus.AUTO_SUBMITTED, 75, BigDecimal.ZERO)
        );
        database.plan(ANSWERS_MARKER).queryRows();
        ExamSubmission automatic = inProgressSubmission();
        assertTrue(automatic.autoSubmit(STARTED.plusMinutes(75)));
        automatic.recordAutomaticScore(BigDecimal.ZERO, STARTED.plusMinutes(75));

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistAutomaticSubmission(automatic);

        assertEquals(SubmissionStatus.AUTO_SUBMITTED, result.getStatus());
        assertEquals(1, database.commitCount);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController earlyDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        Map<Object, Object> longerSource = lockRow();
        longerSource.put("allocated_duration_minutes", 90);
        earlyDatabase.plan(LOCK_INTERNAL_MARKER).queryRows(longerSource);
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(earlyDatabase)
                        .persistAutomaticSubmission(automatic)
        );
        assertEquals("Submission state does not match persisted attempt",
                thrown.getMessage());
        assertEquals(1, earlyDatabase.rollbackCount);
    }

    @Test
    public void entityExtensionPersistsExactDeltaAndAuditWithoutMutatingInput() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(MANAGER_EXTENSION_MARKER).queryRows(row(
                "started_at", STARTED,
                "status", "IN_PROGRESS",
                "allocated_duration_minutes", 75,
                "extra_minutes", 10,
                "execution_opening_time", OPENING,
                "execution_closing_time", CLOSING,
                "execution_status", "SCHEDULED"
        ));
        ExamRepositoryJdbcTestSupport.StatementPlan update = database.plan(
                ENTITY_EXTENSION_MARKER
        ).updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan audit = database.plan(
                AUDIT_MARKER
        ).updateResults(1);
        database.plan(INTERNAL_ENTITY_MARKER).queryRows(
                entityRow(SubmissionStatus.IN_PROGRESS, 15, null)
        );
        database.plan(ANSWERS_MARKER).queryRows();
        ExamSubmission extended = entitySubmission(
                SubmissionStatus.IN_PROGRESS,
                null,
                null,
                null,
                15,
                "Accessibility accommodation",
                EXTENDED,
                List.of()
        );

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistExtension(
                        1002,
                        extended,
                        5,
                        "  Accessibility accommodation  ",
                        EXTENDED
                );

        assertEquals(Map.of(
                1, 15,
                2, "Accessibility accommodation",
                3, EXTENDED,
                4, 501,
                5, 10
        ), update.updateExecutions.get(0));
        assertEquals(Map.of(
                1, 501,
                2, 5,
                3, "Accessibility accommodation",
                4, 1002,
                5, EXTENDED
        ), audit.updateExecutions.get(0));
        assertEquals(15, result.getExtraMinutes());
        assertEquals(15, extended.getExtraMinutes());
        assertEquals(1, database.commitCount);
    }

    @Test
    public void entityAnswerFailureRollsBackAndPreservesJdbcCause() {
        SQLException failure = new SQLException("answer write failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_STUDENT_MARKER).queryRows(lockRow());
        database.plan(QUESTION_MARKER).queryRows(row("question_version_no", 7));
        database.plan(UPSERT_MARKER).updateFailure(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database).persistAnswer(
                        1001,
                        inProgressSubmission(),
                        StudentAnswer.select(501, 30, 7, 4, CURRENT),
                        CURRENT
                )
        );

        assertEquals("Failed to save exam answer", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
    }

    @Test
    public void entityFinalizationFailureRollsBackAnswerAndSubmissionWrites() {
        SQLException failure = new SQLException("final state write failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(LOCK_STUDENT_MARKER).queryRows(lockRow());
        database.plan(QUESTION_MARKER).queryRows(row("question_version_no", 7));
        database.plan(ENTITY_ANSWER_GRADE_MARKER).updateResults(1);
        database.plan(ENTITY_FINALIZE_MARKER).updateFailure(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database)
                        .persistStudentSubmission(1001, gradedManualSubmission())
        );

        assertEquals("Failed to persist student exam submission",
                thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.events.indexOf("update:" + ENTITY_ANSWER_GRADE_MARKER)
                < database.events.indexOf("rollback"));
    }

    @Test
    public void entityExtensionAuditFailureRollsBackDeadlineWrite() {
        SQLException failure = new SQLException("audit write failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(MANAGER_EXTENSION_MARKER).queryRows(row(
                "started_at", STARTED,
                "status", "IN_PROGRESS",
                "allocated_duration_minutes", 75,
                "extra_minutes", 10,
                "execution_opening_time", OPENING,
                "execution_closing_time", CLOSING,
                "execution_status", "SCHEDULED"
        ));
        database.plan(ENTITY_EXTENSION_MARKER).updateResults(1);
        database.plan(AUDIT_MARKER).updateFailure(failure);
        ExamSubmission extended = entitySubmission(
                SubmissionStatus.IN_PROGRESS, null, null, null, 15,
                "Accommodation", EXTENDED, List.of()
        );

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database).persistExtension(
                        1002, extended, 5, "Accommodation", EXTENDED
                )
        );

        assertEquals("Failed to extend submission time", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }

    private static ExamExecution executionEntity() {
        return ExamExecution.rehydrate(
                81, "A1B2", 40, 3, OPENING, CLOSING, 75,
                ExecutionStatus.OPEN, 1002, CREATED, null, null, null,
                List.of(), 0, 0, 0, OPENING, List.of()
        );
    }

    private static ExamSubmission inProgressSubmission() {
        return entitySubmission(
                SubmissionStatus.IN_PROGRESS,
                null,
                null,
                null,
                0,
                null,
                STARTED,
                List.of()
        );
    }

    private static ExamSubmission gradedManualSubmission() {
        StudentAnswer answer = StudentAnswer.rehydrate(
                901, 501, 30, 7, 2, null, null, null,
                STARTED.plusMinutes(1), STARTED.plusMinutes(1)
        );
        ExamSubmission submission = entitySubmission(
                SubmissionStatus.IN_PROGRESS,
                null,
                null,
                null,
                0,
                null,
                STARTED,
                List.of(answer)
        );
        submission.submitManually(SUBMITTED);
        submission.recordAnswerGrade(30, true,
                new BigDecimal("40.00"), GRADED);
        submission.recordAutomaticScore(new BigDecimal("40.00"), GRADED);
        return submission;
    }

    private static ExamSubmission entitySubmission(
            SubmissionStatus status,
            LocalDateTime submittedAt,
            Integer actualDuration,
            BigDecimal score,
            int extraMinutes,
            String extensionReason,
            LocalDateTime updatedAt,
            List<StudentAnswer> answers
    ) {
        return ExamSubmission.rehydrate(
                501, 81, 40, 3, 1001, STARTED, submittedAt, status,
                75, extraMinutes, extensionReason, actualDuration,
                score, score, null, null, null, null, null, null,
                STARTED, updatedAt, answers
        );
    }

    private static Map<Object, Object> lockRow() {
        Map<Object, Object> row = executionRow("OPEN");
        row.put("submission_id", 501);
        row.put("student_user_id", 1001);
        row.put("started_at", STARTED);
        row.put("status", "IN_PROGRESS");
        row.put("allocated_duration_minutes", 75);
        row.put("extra_minutes", 0);
        row.put("execution_status", "OPEN");
        return row;
    }

    private static Map<Object, Object> entityRow(
            SubmissionStatus status,
            int durationOrExtra,
            BigDecimal score
    ) {
        boolean finalized = status != SubmissionStatus.IN_PROGRESS;
        int extraMinutes = finalized ? 0 : durationOrExtra;
        int actualDuration = finalized ? durationOrExtra : 0;
        LocalDateTime submittedAt = finalized
                ? STARTED.plusMinutes(actualDuration) : null;
        LocalDateTime updatedAt = finalized
                ? (submittedAt.isAfter(GRADED) ? submittedAt : GRADED)
                : extraMinutes == 15 ? EXTENDED : STARTED;
        String extensionReason = extraMinutes == 15
                ? "Accessibility accommodation" : null;
        return row(
                "submission_id", 501,
                "execution_id", 81,
                "exam_id", 40,
                "exam_version_no", 3,
                "student_user_id", 1001,
                "started_at", STARTED,
                "submitted_at", submittedAt,
                "status", status.name(),
                "allocated_duration_minutes", 75,
                "extra_minutes", extraMinutes,
                "extension_reason", extensionReason,
                "actual_duration_minutes", finalized ? actualDuration : null,
                "automatic_score", score,
                "final_score", score,
                "teacher_feedback", null,
                "manual_change_reason", null,
                "reviewed_by_user_id", null,
                "reviewed_at", null,
                "published_by_user_id", null,
                "published_at", null,
                "created_at", STARTED,
                "updated_at", updatedAt
        );
    }

    private static Map<Object, Object> answerRow(
            int answerId, int questionId, int versionNo, int orderNumber,
            int selectedOption, Boolean correct, BigDecimal score,
            LocalDateTime updatedAt
    ) {
        return row(
                "answer_id", answerId,
                "submission_id", 501,
                "question_id", questionId,
                "question_version_no", versionNo,
                "selected_option_number", selectedOption,
                "answer_content", null,
                "is_correct", correct,
                "score_received", score,
                "created_at", STARTED.plusMinutes(1),
                "updated_at", updatedAt,
                "order_number", orderNumber
        );
    }

    private static final LocalDateTime OPENING =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime CLOSING =
            LocalDateTime.of(2026, 8, 1, 12, 0);
    private static final LocalDateTime CREATED =
            LocalDateTime.of(2026, 7, 28, 8, 30);
    private static final LocalDateTime STARTED =
            LocalDateTime.of(2026, 8, 1, 9, 15);
    private static final LocalDateTime CURRENT = STARTED.plusMinutes(5);
    private static final LocalDateTime SUBMITTED = STARTED.plusMinutes(30);
    private static final LocalDateTime GRADED = STARTED.plusMinutes(31);
    private static final LocalDateTime EXTENDED = STARTED.plusMinutes(6);
}
