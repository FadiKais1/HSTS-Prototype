package hsts.server.repository;

import hsts.common.type.SubmissionStatus;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.StudentAnswer;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryEntityReadTest {
    private static final String STUDENT_MARKER =
            "AND submission.student_user_id = student.user_id";
    private static final String ACTIVE_MARKER =
            "AND submission.status = 'IN_PROGRESS'";
    private static final String MANAGER_MARKER =
            "JOIN teacher_courses assignment";
    private static final String EXPIRED_MARKER =
            "ORDER BY TIMESTAMPADD(";
    private static final String ANSWERS_MARKER =
            "ORDER BY selection.order_number ASC, answer.answer_id ASC";

    @Test
    public void studentReadHydratesAllAuthoritativeFieldsAndExamOrder() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan submissionQuery = database.plan(
                STUDENT_MARKER
        ).queryRows(inProgressRow());
        ExamRepositoryJdbcTestSupport.StatementPlan answersQuery = database.plan(
                ANSWERS_MARKER
        ).queryRows(
                ungradedAnswer(901, 30, 7, 1, 1),
                ungradedAnswer(902, 10, 2, 2, 2),
                ungradedAnswer(903, 20, 5, 3, 3)
        );

        ExamSubmission submission = new ExamSubmissionRepository(database)
                .findEntityForStudent(1001, 501)
                .orElseThrow();

        assertEquals(Map.of(1, 1001, 2, 501),
                submissionQuery.queryExecutions.get(0));
        assertEquals(Map.of(1, 501), answersQuery.queryExecutions.get(0));
        assertEquals(501, submission.getSubmissionId());
        assertEquals(81, submission.getExecutionId());
        assertEquals(40, submission.getExamId());
        assertEquals(3, submission.getExamVersionNo());
        assertEquals(1001, submission.getStudentUserId());
        assertEquals(STARTED, submission.getStartedAt());
        assertEquals(STARTED.plusMinutes(75), submission.getOriginalDeadline());
        assertEquals(STARTED.plusMinutes(85), submission.getEffectiveDeadline());
        assertEquals(10, submission.getExtraMinutes());
        assertEquals("Accommodation", submission.getExtensionReason());
        assertEquals(CREATED, submission.getCreatedAt());
        assertEquals(UPDATED, submission.getUpdatedAt());
        assertEquals(SubmissionStatus.IN_PROGRESS, submission.getStatus());
        assertNull(submission.getSubmittedAt());
        assertTrue(submission.getAutomaticScoreValue().isEmpty());
        assertTrue(submission.getServerFinalScore().isEmpty());

        List<StudentAnswer> answers = submission.getStudentAnswers();
        assertEquals(List.of(30, 10, 20),
                answers.stream().map(StudentAnswer::getQuestionId).toList());
        assertEquals(List.of(7, 2, 5), answers.stream()
                .map(StudentAnswer::getQuestionVersionNo).toList());
        assertTrue(answers.stream().allMatch(answer -> !answer.isGraded()));
        assertEquals(ANSWER_CREATED, answers.get(0).getCreatedAt());
        assertEquals(ANSWER_UPDATED, answers.get(0).getUpdatedAt());

        String submissionSql = normalized(submissionQuery.sql);
        assertTrue(submissionSql.contains("SUBMISSION.CREATED_AT"));
        assertTrue(submissionSql.contains("SUBMISSION.UPDATED_AT"));
        assertTrue(submissionSql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(submissionSql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertFalse(submissionSql.contains("IDENTITY_NUMBER_HASH"));
        assertFalse(submissionSql.contains("PASSWORD_HASH"));
        assertFalse(submissionSql.contains("CURRENT_VERSION_NO"));

        String answerSql = normalized(answersQuery.sql);
        assertTrue(answerSql.contains(
                "SELECTION.EXAM_VERSION_NO = EXECUTION.EXAM_VERSION_NO"
        ));
        assertTrue(answerSql.contains(
                "SELECTION.QUESTION_VERSION_NO = ANSWER.QUESTION_VERSION_NO"
        ));
        assertTrue(answerSql.contains("ORDER BY SELECTION.ORDER_NUMBER ASC"));
        assertFalse(answerSql.contains("CORRECT_OPTION_NUMBER"));
        assertFalse(answerSql.contains("ANSWER_OPTIONS"));
    }

    @Test
    public void activeAndManagerReadsKeepTheirIndependentScopes() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController activeDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan active = activeDatabase.plan(
                ACTIVE_MARKER
        ).queryRows(inProgressRow());
        activeDatabase.plan(ANSWERS_MARKER).queryRows();

        assertTrue(new ExamSubmissionRepository(activeDatabase)
                .findActiveEntityForStudent(1001, 501).isPresent());
        assertEquals(Map.of(1, 1001, 2, 501), active.queryExecutions.get(0));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController managerDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan manager = managerDatabase.plan(
                MANAGER_MARKER
        ).queryRows(finalizedRow());
        managerDatabase.plan(ANSWERS_MARKER).queryRows(
                gradedAnswer(901, 30, 7, 1, true, "40.00")
        );

        ExamSubmission submission = new ExamSubmissionRepository(managerDatabase)
                .findEntityForManager(1002, 501).orElseThrow();

        assertEquals(Map.of(1, 1002, 2, 501), manager.queryExecutions.get(0));
        assertEquals(SubmissionStatus.SUBMITTED, submission.getStatus());
        assertEquals(new BigDecimal("40.00"),
                submission.getAutomaticScoreValue().orElseThrow());
        assertEquals(new BigDecimal("40.00"),
                submission.getServerFinalScore().orElseThrow());
        StudentAnswer answer = submission.getStudentAnswers().get(0);
        assertEquals(Boolean.TRUE, answer.getCorrectness().orElseThrow());
        assertEquals(new BigDecimal("40.00"),
                answer.getScoreReceivedValue().orElseThrow());

        String sql = normalized(manager.sql);
        assertTrue(sql.contains("MANAGER.ROLE IN ('TEACHER', 'COORDINATOR')"));
        assertTrue(sql.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(sql.contains("ASSIGNMENT.COURSE_ID = EXAM.COURSE_ID"));
    }

    @Test
    public void missingUnauthorizedAndJdbcFailuresFollowRepositoryConventions() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController emptyDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        emptyDatabase.plan(STUDENT_MARKER).queryRows();
        assertTrue(new ExamSubmissionRepository(emptyDatabase)
                .findEntityForStudent(1001, 999).isEmpty());
        assertEquals(1, emptyDatabase.plans.size());

        SQLException failure = new SQLException("submission read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController failedDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        failedDatabase.connectionFailure = failure;
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(failedDatabase)
                        .findEntityForManager(1002, 501)
        );
        assertEquals("Failed to load manager exam submission entity",
                thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }

    @Test
    public void expiredReadIsDeterministicAndDoesNotFinalize() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan expired = database.plan(
                EXPIRED_MARKER
        ).queryRows(inProgressRow(), secondInProgressRow());
        Map<Object, Object> secondAnswer = ungradedAnswer(904, 15, 4, 1, 2);
        secondAnswer.put("submission_id", 502);
        ExamRepositoryJdbcTestSupport.StatementPlan answers = database.plan(
                ANSWERS_MARKER
        ).queryRows(ungradedAnswer(901, 30, 7, 1, 1))
                .queryRows(secondAnswer);
        LocalDateTime now = STARTED.plusMinutes(90);

        List<ExamSubmission> submissions = new ExamSubmissionRepository(database)
                .findExpiredInProgressEntities(now);

        assertEquals(List.of(501, 502), submissions.stream()
                .map(ExamSubmission::getSubmissionId).toList());
        assertTrue(submissions.stream().allMatch(submission ->
                submission.getStatus() == SubmissionStatus.IN_PROGRESS));
        assertEquals(Map.of(1, now), expired.queryExecutions.get(0));
        assertEquals(2, answers.queryExecutions.size());
        assertTrue(database.events.stream().noneMatch(event ->
                event.startsWith("update:") || event.equals("commit")));
    }

    @Test
    public void malformedOrDuplicateAnswerRowsAreRejected() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController duplicateDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        duplicateDatabase.plan(STUDENT_MARKER).queryRows(inProgressRow());
        duplicateDatabase.plan(ANSWERS_MARKER).queryRows(
                ungradedAnswer(901, 30, 7, 1, 1),
                ungradedAnswer(902, 30, 7, 2, 2)
        );
        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(duplicateDatabase)
                        .findEntityForStudent(1001, 501)
        );
        assertEquals("Duplicate student answer: 30", duplicate.getMessage());

        ExamRepositoryJdbcTestSupport.FakeDatabaseController orderDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        orderDatabase.plan(STUDENT_MARKER).queryRows(inProgressRow());
        orderDatabase.plan(ANSWERS_MARKER).queryRows(
                ungradedAnswer(901, 30, 7, 2, 1),
                ungradedAnswer(902, 10, 2, 1, 2)
        );
        IllegalArgumentException malformed = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(orderDatabase)
                        .findEntityForStudent(1001, 501)
        );
        assertEquals("Submission answer order is invalid: 501",
                malformed.getMessage());
    }

    private static Map<Object, Object> inProgressRow() {
        return row(
                "submission_id", 501,
                "execution_id", 81,
                "exam_id", 40,
                "exam_version_no", 3,
                "student_user_id", 1001,
                "started_at", STARTED,
                "submitted_at", null,
                "status", "IN_PROGRESS",
                "allocated_duration_minutes", 75,
                "extra_minutes", 10,
                "extension_reason", "Accommodation",
                "actual_duration_minutes", null,
                "automatic_score", null,
                "final_score", null,
                "teacher_feedback", null,
                "manual_change_reason", null,
                "reviewed_by_user_id", null,
                "reviewed_at", null,
                "published_by_user_id", null,
                "published_at", null,
                "created_at", CREATED,
                "updated_at", UPDATED
        );
    }

    private static Map<Object, Object> secondInProgressRow() {
        Map<Object, Object> row = inProgressRow();
        row.put("submission_id", 502);
        row.put("student_user_id", 1004);
        return row;
    }

    private static Map<Object, Object> finalizedRow() {
        Map<Object, Object> row = inProgressRow();
        row.put("submitted_at", STARTED.plusMinutes(45));
        row.put("status", "SUBMITTED");
        row.put("actual_duration_minutes", 45);
        row.put("automatic_score", new BigDecimal("40.00"));
        row.put("final_score", new BigDecimal("40.00"));
        row.put("updated_at", STARTED.plusMinutes(46));
        return row;
    }

    private static Map<Object, Object> ungradedAnswer(
            int answerId, int questionId, int versionNo,
            int orderNumber, int selectedOption
    ) {
        return answerRow(
                answerId, questionId, versionNo, orderNumber,
                selectedOption, null, null
        );
    }

    private static Map<Object, Object> gradedAnswer(
            int answerId, int questionId, int versionNo,
            int orderNumber, boolean correct, String score
    ) {
        return answerRow(
                answerId, questionId, versionNo, orderNumber,
                2, correct, new BigDecimal(score)
        );
    }

    private static Map<Object, Object> answerRow(
            int answerId, int questionId, int versionNo,
            int orderNumber, int selectedOption,
            Boolean correct, BigDecimal score
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
                "created_at", ANSWER_CREATED,
                "updated_at", ANSWER_UPDATED,
                "order_number", orderNumber
        );
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }

    private static final LocalDateTime STARTED =
            LocalDateTime.of(2026, 8, 1, 9, 15);
    private static final LocalDateTime CREATED = STARTED.plusSeconds(1);
    private static final LocalDateTime UPDATED = STARTED.plusMinutes(10);
    private static final LocalDateTime ANSWER_CREATED = STARTED.plusMinutes(1);
    private static final LocalDateTime ANSWER_UPDATED = STARTED.plusMinutes(2);
}
