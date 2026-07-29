package hsts.server.repository;

import hsts.common.PublishedExamQuestionReviewDTO;
import hsts.common.PublishedExamReviewDTO;
import hsts.common.type.PublishedAnswerOutcome;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.normalized;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryPublishedReviewTest {
    private static final String HEADER_MARKER = "expected_question_count";
    private static final String QUESTIONS_MARKER = "option_4.option_number";
    private static final LocalDateTime SUBMITTED =
            LocalDateTime.of(2026, 8, 30, 10, 0);
    private static final LocalDateTime REVIEWED = SUBMITTED.plusMinutes(5);
    private static final LocalDateTime PUBLISHED = REVIEWED.plusMinutes(5);

    @Test
    public void mapsExactPublishedSnapshotInExamOrderIncludingUnansweredQuestions() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database(
                questionRow(30, 7, 1, 901, 2, false, new BigDecimal("0.00")),
                questionRow(10, 2, 2, null, null, null, null)
        );

        PublishedExamReviewDTO review = new ExamSubmissionRepository(database)
                .findPublishedExamReviewForStudent(1001, 501).orElseThrow();

        assertEquals(501, review.getSubmissionId());
        assertEquals(81, review.getExecutionId());
        assertEquals("RLYQ", review.getExecutionCode());
        assertEquals(40, review.getExamId());
        assertEquals(3, review.getExamVersionNo());
        assertEquals("Historical Final", review.getExamTitle());
        assertEquals(7, review.getCourseId());
        assertEquals("Mathematics", review.getCourseName());
        assertEquals(new BigDecimal("65.00"), review.getFinalScore());
        assertEquals("Strong work", review.getTeacherFeedback());
        assertEquals(SUBMITTED, review.getSubmittedAt());
        assertEquals(REVIEWED, review.getReviewedAt());
        assertEquals(PUBLISHED, review.getPublishedAt());
        assertEquals(List.of(30, 10), review.getQuestions().stream()
                .map(PublishedExamQuestionReviewDTO::getQuestionId).toList());
        PublishedExamQuestionReviewDTO answered = review.getQuestions().get(0);
        assertEquals(7, answered.getQuestionVersionNo());
        assertEquals(Integer.valueOf(2), answered.getSelectedOptionNumber());
        assertEquals(3, answered.getCorrectOptionNumber());
        assertEquals(PublishedAnswerOutcome.INCORRECT, answered.getOutcome());
        assertEquals(new BigDecimal("0.00"), answered.getAwardedScore());
        assertEquals(new BigDecimal("50.00"), answered.getMaximumScore());
        assertEquals(List.of("One", "Two", "Three", "Four"),
                answered.getAnswerOptions());
        PublishedExamQuestionReviewDTO unanswered = review.getQuestions().get(1);
        assertEquals(PublishedAnswerOutcome.UNANSWERED, unanswered.getOutcome());
        assertEquals(new BigDecimal("0.00"), unanswered.getAwardedScore());
        assertThrows(UnsupportedOperationException.class,
                () -> review.getQuestions().clear());

        ExamRepositoryJdbcTestSupport.StatementPlan header = database.plans.get(0);
        ExamRepositoryJdbcTestSupport.StatementPlan questions = database.plans.get(1);
        assertEquals(Map.of(1, 1001, 2, 501), header.queryExecutions.get(0));
        assertEquals(Map.of(1, 501, 2, 40, 3, 3),
                questions.queryExecutions.get(0));
        assertSafeHeaderSql(header.sql);
        assertExactQuestionSql(questions.sql);
        assertReadOnly(database, header.sql, questions.sql);
    }

    @Test
    public void missingOrUnauthorizedHeaderReturnsEmptyBeforeCorrectAnswersAreLoaded() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan header = database
                .plan(HEADER_MARKER).queryRows();

        assertTrue(new ExamSubmissionRepository(database)
                .findPublishedExamReviewForStudent(1002, 501).isEmpty());
        assertEquals(1, database.plans.size());
        assertEquals(Map.of(1, 1002, 2, 501), header.queryExecutions.get(0));
        assertSafeHeaderSql(header.sql);
    }

    @Test
    public void malformedAnswerSnapshotAndIncompleteQuestionSetAreRejected() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController inconsistent = database(
                questionRow(30, 7, 1, 901, 3, false, new BigDecimal("0.00")),
                questionRow(10, 2, 2, null, null, null, null)
        );
        assertEquals(
                "Published answer correctness is inconsistent: 30",
                assertThrows(IllegalArgumentException.class,
                        () -> new ExamSubmissionRepository(inconsistent)
                                .findPublishedExamReviewForStudent(1001, 501))
                        .getMessage()
        );

        ExamRepositoryJdbcTestSupport.FakeDatabaseController incomplete =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        incomplete.plan(HEADER_MARKER).queryRows(headerRow(2));
        incomplete.plan(QUESTIONS_MARKER).queryRows(
                questionRow(30, 7, 1, 901, 2, false, new BigDecimal("0.00"))
        );
        assertEquals(
                "Published exam review question snapshot is incomplete: 501",
                assertThrows(IllegalArgumentException.class,
                        () -> new ExamSubmissionRepository(incomplete)
                                .findPublishedExamReviewForStudent(1001, 501))
                        .getMessage()
        );
    }

    @Test
    public void idsValidateBeforeJdbcAndSqlFailuresPreserveCause() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController unused =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamSubmissionRepository repository = new ExamSubmissionRepository(unused);
        assertEquals("Student user ID must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> repository.findPublishedExamReviewForStudent(0, 501))
                        .getMessage());
        assertEquals("Submission ID must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> repository.findPublishedExamReviewForStudent(1001, 0))
                        .getMessage());
        assertTrue(unused.plans.isEmpty());

        SQLException cause = new SQLException("database unavailable");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController failed =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        failed.plan(HEADER_MARKER).queryFailure(cause);
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(failed)
                        .findPublishedExamReviewForStudent(1001, 501)
        );
        assertEquals("Failed to load published exam review", thrown.getMessage());
        assertSame(cause, thrown.getCause());
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController database(
            Map<Object, Object>... questionRows
    ) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(HEADER_MARKER).queryRows(headerRow(questionRows.length));
        database.plan(QUESTIONS_MARKER).queryRows(questionRows);
        return database;
    }

    private static Map<Object, Object> headerRow(int questionCount) {
        return row(
                "submission_id", 501, "execution_id", 81,
                "execution_code", "RLYQ", "exam_id", 40,
                "exam_version_no", 3, "exam_title", "Historical Final",
                "course_id", 7, "course_name", "Mathematics",
                "status", "PUBLISHED", "final_score", new BigDecimal("65.00"),
                "teacher_feedback", "Strong work", "submitted_at", SUBMITTED,
                "reviewed_at", REVIEWED, "published_at", PUBLISHED,
                "expected_question_count", questionCount, "invalid_answer_count", 0
        );
    }

    private static Map<Object, Object> questionRow(
            int questionId, int version, int order, Integer answerId,
            Integer selected, Boolean correct, BigDecimal score
    ) {
        return row(
                "order_number", order, "question_id", questionId,
                "question_version_no", version, "content", "Question " + questionId,
                "topic", "Algebra", "difficulty", "HARD",
                "question_type", "MULTIPLE_CHOICE", "illustration_path", "",
                "correct_option_number", 3, "maximum_score", new BigDecimal("50.00"),
                "option_count", 4, "answer_option_1", "One",
                "answer_option_2", "Two", "answer_option_3", "Three",
                "answer_option_4", "Four", "answer_id", answerId,
                "answered_question_version_no", answerId == null ? null : version,
                "selected_option_number", selected, "is_correct", correct,
                "awarded_score", score
        );
    }

    private static void assertSafeHeaderSql(String sql) {
        String value = normalized(sql);
        assertTrue(value.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(value.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(value.contains("SUBMISSION.STUDENT_USER_ID = STUDENT.USER_ID"));
        assertTrue(value.contains("SUBMISSION.STATUS = 'PUBLISHED'"));
        assertTrue(value.contains("VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"));
        assertFalse(value.contains("CURRENT_VERSION_NO"));
        for (String forbidden : List.of(
                "PASSWORD_HASH", "IDENTITY_NUMBER_HASH", "AUTOMATIC_SCORE",
                "MANUAL_CHANGE_REASON", "REVIEWED_BY_USER_ID", "PUBLISHED_BY_USER_ID"
        )) assertFalse(value.contains(forbidden));
    }

    private static void assertExactQuestionSql(String sql) {
        String value = normalized(sql);
        assertTrue(value.contains("SELECTION.EXAM_VERSION_NO = ?"));
        assertTrue(value.contains("QUESTION_VERSION.VERSION_NO = SELECTION.QUESTION_VERSION_NO"));
        assertTrue(value.contains("OPTION_1.OPTION_NUMBER = 1"));
        assertTrue(value.contains("OPTION_4.OPTION_NUMBER = 4"));
        assertTrue(value.contains("ORDER BY SELECTION.ORDER_NUMBER ASC"));
        assertFalse(value.contains("CURRENT_VERSION_NO"));
        assertFalse(value.contains("USERS "));
    }

    private static void assertReadOnly(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database,
            String... statements
    ) {
        assertEquals(0, database.autoCommitSetCalls);
        assertEquals(0, database.commitCount);
        assertEquals(0, database.rollbackCount);
        for (String sql : statements) {
            String value = normalized(sql);
            assertFalse(value.contains("FOR UPDATE"));
            assertFalse(value.startsWith("UPDATE "));
            assertFalse(value.startsWith("INSERT "));
            assertFalse(value.startsWith("DELETE "));
        }
    }
}
