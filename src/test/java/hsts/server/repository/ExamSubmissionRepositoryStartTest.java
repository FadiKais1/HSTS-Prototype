package hsts.server.repository;

import hsts.common.ExamAttemptDTO;
import hsts.common.StudentExamQuestionDTO;
import hsts.common.type.SubmissionStatus;
import hsts.server.entity.ExamSubmission;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.CLOSING;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.OPENING;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.STARTED;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.completeSubmissionRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.executionRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.executionSubmissionRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.executionEntity;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.entityAnswerRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.normalized;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.planInternalEntity;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.planSafeAttempt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryStartTest {
    private static final String EXECUTION_MARKER = "WHERE execution.execution_id = ?";
    private static final String EXISTING_MARKER = "WHERE submission.execution_id = ?";
    private static final String INSERT_MARKER = "INSERT INTO exam_submissions (";
    private static final String STARTED_COUNT_MARKER =
            "SET started_count = started_count + 1";

    @Test
    public void startsOneStudentScopedAttemptAndMapsOnlyImmutableSafeData() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        LocalDateTime now = STARTED;
        ExamRepositoryJdbcTestSupport.StatementPlan execution = database.plan(
                EXECUTION_MARKER
        ).queryRows(executionRow("SCHEDULED"));
        database.plan(EXISTING_MARKER).queryRows();
        ExamRepositoryJdbcTestSupport.StatementPlan insert = database.plan(INSERT_MARKER)
                .updateResults(1).generatedKey(501);
        ExamRepositoryJdbcTestSupport.StatementPlan count = database.plan(
                STARTED_COUNT_MARKER
        ).updateResults(1);
        planInternalEntity(database, "IN_PROGRESS", now, 0);
        database.plan("AND submission.status = 'IN_PROGRESS'")
                .queryRows(completeSubmissionRow("IN_PROGRESS", now, 75, 0));
        ExamSubmissionRepositoryTestSupport.SafeAttemptPlans safe = planSafeAttempt(database);
        ExamSubmissionRepository repository = new ExamSubmissionRepository(database);

        ExamSubmission entity = repository.startOrResume(1001, executionEntity(), now);
        ExamAttemptDTO attempt = repository.findActiveForStudent(1001, 501, now)
                .orElseThrow();

        assertEquals(501, entity.getSubmissionId());
        assertEquals(81, entity.getExecutionId());
        assertAttempt(attempt, now, now.plusMinutes(75), 75 * 60L);
        assertEquals(Map.of(1, 1001, 2, 81), execution.queryExecutions.get(0));
        String executionSql = normalized(execution.sql);
        assertTrue(executionSql.contains("JOIN STUDENT_COURSES ENROLLMENT"));
        assertTrue(executionSql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(executionSql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(executionSql.contains("VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"));
        assertTrue(executionSql.contains("FOR UPDATE"));

        Map<Integer, Object> inserted = insert.updateExecutions.get(0);
        assertEquals(81, inserted.get(1));
        assertEquals(1001, inserted.get(2));
        assertEquals(now, inserted.get(3));
        assertEquals(null, inserted.get(4));
        assertEquals("IN_PROGRESS", inserted.get(5));
        assertEquals(75, inserted.get(6));
        assertEquals(0, inserted.get(7));
        for (int index = 8; index <= 17; index++) {
            assertTrue(inserted.containsKey(index));
            assertEquals(null, inserted.get(index));
        }
        assertEquals(Map.of(1, 81), count.updateExecutions.get(0));
        assertEquals(2, database.connectionRequests);
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.autoCommit);

        String safeSql = normalized(safe.questions.sql);
        assertTrue(safeSql.contains("QUESTION_VERSIONS VERSION"));
        assertTrue(safeSql.contains("SELECTION.QUESTION_VERSION_NO"));
        assertTrue(safeSql.contains("OPTION_4.OPTION_TEXT AS ANSWER_OPTION_4"));
        assertTrue(safeSql.contains("ORDER BY SELECTION.ORDER_NUMBER ASC"));
        assertFalse(safeSql.contains("CORRECT_OPTION_NUMBER"));
        assertFalse(safeSql.contains("TEACHER_NOTES"));
        assertTrue(Arrays.stream(StudentExamQuestionDTO.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("correctOptionNumber")));
        assertTrue(Arrays.stream(ExamAttemptDTO.class.getDeclaredFields())
                .noneMatch(field -> field.getName().contains("Score")));
    }

    @Test
    public void resumePreservesTimingAnswersAndSkipsInsertAndStartedCounterAfterClosing() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(EXECUTION_MARKER).queryRows(executionRow("CLOSED"));
        LocalDateTime lateStart = CLOSING.minusMinutes(30);
        database.plan(EXISTING_MARKER).queryRows(
                executionSubmissionRow("IN_PROGRESS", lateStart, 75, 10)
        );
        planInternalEntity(
                database, "IN_PROGRESS", lateStart, 10,
                entityAnswerRow(901, 17, 4, 1, 2, lateStart.plusMinutes(5))
        );
        LocalDateTime now = CLOSING.plusMinutes(1);

        ExamSubmission attempt = new ExamSubmissionRepository(database)
                .startOrResume(1001, executionEntity(), now);

        assertEquals(lateStart, attempt.getStartedAt());
        assertEquals(lateStart.plusMinutes(85), attempt.getEffectiveDeadline());
        assertEquals(10, attempt.getExtraMinutes());
        assertEquals(1, attempt.getStudentAnswers().size());
        assertTrue(database.plans.stream().allMatch(plan ->
                plan.updateExecutions.isEmpty()));
        assertEquals(1, database.commitCount);
    }

    @Test
    public void closingBlocksNewStartAndFinalizedOrExpiredAttemptCannotResume() {
        assertStartDomainFailure(
                executionRow("CLOSED"),
                null,
                CLOSING.plusSeconds(1),
                "Execution not available"
        );
        assertStartDomainFailure(
                executionRow("OPEN"),
                executionSubmissionRow("SUBMITTED", STARTED, 75, 0),
                STARTED.plusMinutes(10),
                "Exam attempt already submitted"
        );
        assertStartDomainFailure(
                executionRow("OPEN"),
                executionSubmissionRow("IN_PROGRESS", STARTED, 75, 0),
                STARTED.plusMinutes(75),
                "Execution not available"
        );
    }

    @Test
    public void inaccessibleExecutionUsesExactDomainMessage() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(EXECUTION_MARKER).queryRows();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database)
                        .startOrResume(1001, executionEntity(), STARTED)
        );

        assertEquals("Execution not available", thrown.getMessage());
        assertEquals(null, thrown.getCause());
        assertEquals(1, database.rollbackCount);
    }

    @Test
    public void uniqueStartRaceRereadsExistingAttemptWithoutIncrementingCount() {
        SQLException collision = new SQLException(
                "Duplicate entry for key 'uq_exam_submissions_execution_student'",
                "23000",
                1062
        );
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(EXECUTION_MARKER).queryRows(executionRow("OPEN"));
        database.plan(EXISTING_MARKER)
                .queryRows()
                .queryRows(executionSubmissionRow("IN_PROGRESS", STARTED, 75, 0));
        database.plan(INSERT_MARKER).updateFailure(collision);
        planInternalEntity(database, "IN_PROGRESS", STARTED, 0);

        ExamSubmission attempt = new ExamSubmissionRepository(database)
                .startOrResume(1001, executionEntity(), STARTED.plusMinutes(1));

        assertEquals(501, attempt.getSubmissionId());
        assertEquals(STARTED, attempt.getStartedAt());
        assertTrue(database.plans.stream()
                .noneMatch(plan -> plan.marker.contains("started_count")));
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
    }

    @Test
    public void jdbcFailureWrapsExactlyAndSuppressesRollbackAndRestorationFailures() {
        SQLException original = new SQLException("execution read failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(EXECUTION_MARKER).queryFailure(original);
        database.rollbackFailure = rollbackFailure;
        database.restorationFailure = restorationFailure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database)
                        .startOrResume(1001, executionEntity(), STARTED)
        );

        assertEquals("Failed to start exam attempt", thrown.getMessage());
        assertSame(original, thrown.getCause());
        assertEquals(2, original.getSuppressed().length);
        assertSame(rollbackFailure, original.getSuppressed()[0]);
        assertSame(restorationFailure, original.getSuppressed()[1]);
        assertEquals(1, database.connectionRequests);
    }

    private static void assertStartDomainFailure(Map<Object, Object> execution,
                                                 Map<Object, Object> submission,
                                                 LocalDateTime now,
                                                 String message) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(EXECUTION_MARKER).queryRows(execution);
        ExamRepositoryJdbcTestSupport.StatementPlan existing = database.plan(
                EXISTING_MARKER
        );
        if (submission == null) {
            existing.queryRows();
        } else {
            existing.queryRows(submission);
        }

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database)
                        .startOrResume(1001, executionEntity(), now)
        );

        assertEquals(message, thrown.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }

    private static void assertAttempt(ExamAttemptDTO attempt, LocalDateTime startedAt,
                                      LocalDateTime deadline, long remainingSeconds) {
        assertEquals(501, attempt.getSubmissionId());
        assertEquals(81, attempt.getExecutionId());
        assertEquals("A1B2", attempt.getExecutionCode());
        assertEquals(40, attempt.getExamId());
        assertEquals(3, attempt.getExamVersionNo());
        assertEquals("Approved Midterm", attempt.getExamTitle());
        assertEquals("Read each question carefully", attempt.getStudentInstructions());
        assertEquals(startedAt, attempt.getStartedAt());
        assertEquals(deadline, attempt.getDeadline());
        assertEquals(75, attempt.getAllocatedDurationMinutes());
        assertEquals(0, attempt.getExtraMinutes());
        assertEquals(remainingSeconds, attempt.getRemainingSeconds());
        assertEquals(SubmissionStatus.IN_PROGRESS, attempt.getStatus());
        assertEquals(2, attempt.getQuestions().size());
        assertEquals(17, attempt.getQuestions().get(0).getQuestionId());
        assertEquals(4, attempt.getQuestions().get(0).getQuestionVersionNo());
        assertEquals("Historical question", attempt.getQuestions().get(0).getContent());
        assertEquals("One", attempt.getQuestions().get(0).getAnswerOption1());
        assertEquals("Four", attempt.getQuestions().get(0).getAnswerOption4());
        assertEquals(1, attempt.getAnswers().size());
        assertEquals(17, attempt.getAnswers().get(0).getQuestionId());
        assertEquals(Integer.valueOf(2),
                attempt.getAnswers().get(0).getSelectedOptionNumber());
    }
}
