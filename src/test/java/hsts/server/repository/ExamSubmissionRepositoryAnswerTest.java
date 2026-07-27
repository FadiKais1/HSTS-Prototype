package hsts.server.repository;

import hsts.server.entity.ExamSubmission;
import hsts.server.entity.StudentAnswer;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.STARTED;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.completeSubmissionRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.entityAnswerRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.entityLockRow;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.inProgressSubmission;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.normalized;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.planInternalEntity;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryAnswerTest {
    private static final String SUBMISSION_MARKER =
            "AND submission.student_user_id = ? FOR UPDATE";
    private static final String QUESTION_MARKER =
            "SELECT selection.question_version_no";
    private static final String ANSWER_MARKER = "INSERT INTO student_answers (";

    @Test
    public void nullEntityFailsBeforeConnection() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();

        NullPointerException thrown = assertThrows(
                NullPointerException.class,
                () -> new ExamSubmissionRepository(database).persistAnswer(
                        1001, null, null, STARTED
                )
        );

        assertEquals("Exam submission is required", thrown.getMessage());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void upsertsImmutableVersionAnswerWithoutCalculatingCorrectness() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController(false);
        ExamRepositoryJdbcTestSupport.StatementPlan submissionLock = database.plan(
                SUBMISSION_MARKER
        ).queryRows(entityLockRow("IN_PROGRESS", STARTED, 75, 5));
        ExamRepositoryJdbcTestSupport.StatementPlan question = database.plan(
                QUESTION_MARKER
        ).queryRows(row("question_version_no", 4));
        ExamRepositoryJdbcTestSupport.StatementPlan answer = database.plan(ANSWER_MARKER)
                .updateResults(2);
        LocalDateTime now = STARTED.plusMinutes(20);
        planInternalEntity(
                database, "IN_PROGRESS", STARTED, 5,
                entityAnswerRow(901, 17, 4, 1, 3, now)
        );
        ExamSubmission submission = inProgressSubmission(STARTED, 5);
        StudentAnswer selected = StudentAnswer.select(501, 17, 4, 3, now);

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistAnswer(1001, submission, selected, now);

        assertEquals(17, result.getStudentAnswers().get(0).getQuestionId());
        assertEquals(3, result.getStudentAnswers().get(0).getSelectedOptionNumber());
        assertEquals(now, result.getStudentAnswers().get(0).getUpdatedAt());
        assertEquals(Map.of(1, 501, 2, 1001), submissionLock.queryExecutions.get(0));
        assertEquals(Map.of(1, 40, 2, 3, 3, 17), question.queryExecutions.get(0));
        String submissionSql = normalized(submissionLock.sql);
        assertTrue(submissionSql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(submissionSql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(submissionSql.contains("FOR UPDATE"));

        Map<Integer, Object> values = answer.updateExecutions.get(0);
        assertEquals(501, values.get(1));
        assertEquals(17, values.get(2));
        assertEquals(4, values.get(3));
        assertEquals(3, values.get(4));
        assertEquals(null, values.get(5));
        assertEquals(null, values.get(6));
        assertEquals(null, values.get(7));
        assertEquals(now, values.get(8));
        assertEquals(now, values.get(9));
        String answerSql = normalized(answer.sql);
        assertTrue(answerSql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(answerSql.contains("IS_CORRECT = NULL"));
        assertTrue(answerSql.contains("SCORE_RECEIVED = NULL"));
        assertEquals(1, database.connectionRequests);
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(!database.autoCommit);
    }

    @Test
    public void missingFinalizedAndExpiredAttemptsUseExactErrors() {
        assertSubmissionFailure(null, STARTED.plusMinutes(1),
                IllegalArgumentException.class, "Exam attempt not found: 501");
        assertSubmissionFailure(
                completeSubmissionRow("SUBMITTED", STARTED, 75, 0),
                STARTED.plusMinutes(1),
                IllegalStateException.class,
                "Exam attempt already submitted"
        );
        assertSubmissionFailure(
                completeSubmissionRow("IN_PROGRESS", STARTED, 75, 0),
                STARTED.plusMinutes(75),
                IllegalStateException.class,
                "Exam time has expired"
        );
    }

    @Test
    public void missingQuestionPrecedesOptionValidationAndUsesExactError() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(SUBMISSION_MARKER).queryRows(
                entityLockRow("IN_PROGRESS", STARTED, 75, 0)
        );
        database.plan(QUESTION_MARKER).queryRows();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(database).persistAnswer(
                        1001,
                        inProgressSubmission(STARTED, 0),
                        StudentAnswer.select(501, 99, 7, 4,
                                STARTED.plusMinutes(1)),
                        STARTED.plusMinutes(1)
                )
        );

        assertEquals("Question not found in exam: 99", thrown.getMessage());
        assertEquals(1, database.rollbackCount);
    }

    @Test
    public void jdbcFailureWrapsExactlyRollsBackAndPreservesCause() {
        SQLException failure = new SQLException("answer write failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(SUBMISSION_MARKER).queryRows(
                entityLockRow("IN_PROGRESS", STARTED, 75, 0)
        );
        database.plan(QUESTION_MARKER).queryRows(row("question_version_no", 4));
        database.plan(ANSWER_MARKER).updateFailure(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database).persistAnswer(
                        1001,
                        inProgressSubmission(STARTED, 0),
                        StudentAnswer.select(501, 17, 4, 2,
                                STARTED.plusMinutes(1)),
                        STARTED.plusMinutes(1)
                )
        );

        assertEquals("Failed to save exam answer", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
    }

    private static void assertSubmissionFailure(Map<Object, Object> submissionRow,
                                                LocalDateTime now,
                                                Class<? extends RuntimeException> type,
                                                String message) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan submission = database.plan(
                SUBMISSION_MARKER
        );
        if (submissionRow == null) {
            submission.queryRows();
        } else {
            submission.queryRows(submissionRow);
        }

        RuntimeException thrown = assertThrows(
                type,
                () -> new ExamSubmissionRepository(database).persistAnswer(
                        1001,
                        inProgressSubmission(STARTED, 0),
                        StudentAnswer.select(501, 17, 4, 2, now),
                        now
                )
        );

        assertEquals(message, thrown.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }
}
