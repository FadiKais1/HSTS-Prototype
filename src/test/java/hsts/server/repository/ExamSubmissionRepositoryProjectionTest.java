package hsts.server.repository;

import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PublishedGradeDTO;
import hsts.common.PublishedGradeSummaryDTO;
import hsts.common.SubmissionAnswerReviewDTO;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.SubmissionStatus;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryProjectionTest {
    private static final String MANAGER_LIST_MARKER =
            "ORDER BY student.full_name ASC";
    private static final String MANAGER_DETAIL_MARKER =
            "submission.manual_change_reason";
    private static final String ANSWER_REVIEW_MARKER =
            "answer.answer_id";
    private static final String STUDENT_LIST_MARKER =
            "ORDER BY submission.published_at DESC";
    private static final String STUDENT_DETAIL_MARKER =
            "submission.teacher_feedback";

    private static final LocalDateTime STARTED =
            LocalDateTime.of(2026, 8, 12, 9, 0);
    private static final LocalDateTime SUBMITTED = STARTED.plusMinutes(45);
    private static final LocalDateTime REVIEWED = SUBMITTED.plusMinutes(5);
    private static final LocalDateTime UPDATED = REVIEWED.plusMinutes(1);
    private static final LocalDateTime PUBLISHED = REVIEWED.plusMinutes(5);

    @Test
    public void managerSummaryListMapsTeacherAndCoordinatorScopeInDatabaseOrder() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController teacherDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan teacherPlan = teacherDatabase
                .plan(MANAGER_LIST_MARKER)
                .queryRows(
                        managerSummaryRow(502, 1001, "Alice Student", 3),
                        managerSummaryRow(501, 1002, "Bob Student", 3)
                );

        List<ExecutionSubmissionSummaryDTO> teacherResults =
                new ExamSubmissionRepository(teacherDatabase)
                        .findSummariesForManager(1002, 81);

        assertEquals(2, teacherResults.size());
        assertSummary(teacherResults.get(0), 502, 1001, "Alice Student");
        assertSummary(teacherResults.get(1), 501, 1002, "Bob Student");
        assertThrows(UnsupportedOperationException.class, teacherResults::clear);
        assertEquals(Map.of(1, 1002, 2, 81), teacherPlan.queryExecutions.get(0));
        assertManagerAuthorizationAndExactVersion(teacherPlan.sql);
        assertReadOnly(teacherDatabase, teacherPlan.sql);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController coordinatorDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan coordinatorPlan = coordinatorDatabase
                .plan(MANAGER_LIST_MARKER)
                .queryRows(managerSummaryRow(503, 1003, "Cara Student", 3));
        List<ExecutionSubmissionSummaryDTO> coordinatorResults =
                new ExamSubmissionRepository(coordinatorDatabase)
                        .findSummariesForManager(1003, 81);
        assertEquals(1, coordinatorResults.size());
        assertEquals(Map.of(1, 1003, 2, 81),
                coordinatorPlan.queryExecutions.get(0));
        assertManagerAuthorizationAndExactVersion(coordinatorPlan.sql);
    }

    @Test
    public void deniedManagersAndMissingExecutionSeeEmptyList() {
        for (int managerId : List.of(1002, 1001, 1004, 9999)) {
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                    new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
            ExamRepositoryJdbcTestSupport.StatementPlan plan = database
                    .plan(MANAGER_LIST_MARKER)
                    .queryRows();
            assertTrue(new ExamSubmissionRepository(database)
                    .findSummariesForManager(managerId, 81)
                    .isEmpty());
            assertEquals(Map.of(1, managerId, 2, 81),
                    plan.queryExecutions.get(0));
            assertReadOnly(database, plan.sql);
        }
    }

    @Test
    public void managerDetailMapsCompleteStateAndEveryExamQuestionInExamOrder() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan header = database
                .plan(MANAGER_DETAIL_MARKER)
                .queryRows(reviewHeaderRow("SUBMITTED"));
        ExamRepositoryJdbcTestSupport.StatementPlan answers = database
                .plan(ANSWER_REVIEW_MARKER)
                .queryRows(
                        answeredReviewRow(30, 7, 1, 901, 7, "Wrong choice"),
                        unansweredReviewRow(10, 2, 2)
                );

        SubmissionReviewDTO review = new ExamSubmissionRepository(database)
                .findReviewForManager(1002, 501)
                .orElseThrow();

        assertEquals(501, review.getSubmissionId());
        assertEquals(81, review.getExecutionId());
        assertEquals(40, review.getExamId());
        assertEquals(3, review.getExamVersionNo());
        assertEquals("Historical Algebra Final", review.getExamTitle());
        assertEquals(1001, review.getStudentUserId());
        assertEquals("Development Student", review.getStudentName());
        assertEquals(SubmissionStatus.SUBMITTED, review.getStatus());
        assertEquals(new BigDecimal("60.00"), review.getAutomaticScore());
        assertEquals(new BigDecimal("65.00"), review.getFinalScore());
        assertEquals("Strong work", review.getTeacherFeedback());
        assertEquals("Accepted alternate method", review.getAdjustmentReason());
        assertEquals(1002, review.getReviewerUserId());
        assertEquals(STARTED, review.getStartedAt());
        assertEquals(SUBMITTED, review.getSubmittedAt());
        assertEquals(REVIEWED, review.getReviewedAt());
        assertEquals(UPDATED, review.getUpdatedAt());
        assertEquals(0, review.getPublisherUserId());
        assertNull(review.getPublishedAt());
        assertEquals(List.of(30, 10), review.getAnswers().stream()
                .map(SubmissionAnswerReviewDTO::getQuestionId).toList());
        assertEquals("Wrong choice", review.getAnswers().get(0)
                .getSelectedOptionText());
        assertEquals(Boolean.FALSE, review.getAnswers().get(0).getCorrect());
        assertEquals(new BigDecimal("0.00"), review.getAnswers().get(0)
                .getAwardedScore());
        assertNull(review.getAnswers().get(1).getSelectedOptionText());
        assertNull(review.getAnswers().get(1).getCorrect());
        assertNull(review.getAnswers().get(1).getAwardedScore());
        assertThrows(UnsupportedOperationException.class,
                () -> review.getAnswers().clear());
        assertEquals(Map.of(1, 1002, 2, 501), header.queryExecutions.get(0));
        assertEquals(Map.of(1, 501, 2, 40, 3, 3),
                answers.queryExecutions.get(0));
        assertManagerAuthorizationAndExactVersion(header.sql);
        assertTrue(normalized(header.sql).contains("SUBMISSION.UPDATED_AT"));
        assertExactAnswerVersionWithoutCorrectOption(answers.sql);
        assertReadOnly(database, header.sql, answers.sql);
    }

    @Test
    public void managerDetailMissingUnauthorizedAndMalformedStatusAreSafe() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan missing = missingDatabase
                .plan(MANAGER_DETAIL_MARKER)
                .queryRows();
        assertTrue(new ExamSubmissionRepository(missingDatabase)
                .findReviewForManager(1004, 501)
                .isEmpty());
        assertEquals(1, missingDatabase.plans.size());
        assertReadOnly(missingDatabase, missing.sql);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController malformedDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        malformedDatabase.plan(MANAGER_DETAIL_MARKER)
                .queryRows(reviewHeaderRow("BROKEN"));
        IllegalArgumentException malformed = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(malformedDatabase)
                        .findReviewForManager(1002, 501)
        );
        assertEquals("Submission status is invalid: 501", malformed.getMessage());

        ExamRepositoryJdbcTestSupport.FakeDatabaseController nullUpdatedDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        Map<Object, Object> nullUpdatedRow = reviewHeaderRow("SUBMITTED");
        nullUpdatedRow.put("updated_at", null);
        nullUpdatedDatabase.plan(MANAGER_DETAIL_MARKER).queryRows(nullUpdatedRow);
        IllegalArgumentException nullUpdated = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(nullUpdatedDatabase)
                        .findReviewForManager(1002, 501)
        );
        assertEquals(
                "Submission updated timestamp is invalid: 501",
                nullUpdated.getMessage()
        );
    }

    @Test
    public void managerDetailRejectsDuplicateSnapshotStateAndWrongAnswerVersion() {
        assertMalformedAnswers(
                answeredReviewRow(30, 7, 1, 901, 7, "Choice"),
                answeredReviewRow(10, 2, 1, 902, 2, "Choice")
        );
        assertMalformedAnswers(
                answeredReviewRow(30, 7, 1, 901, 7, "Choice"),
                answeredReviewRow(30, 7, 2, 902, 7, "Choice")
        );

        ExamRepositoryJdbcTestSupport.FakeDatabaseController versionDatabase =
                reviewDatabase(answeredReviewRow(30, 7, 1, 901, 8, "Choice"));
        IllegalArgumentException wrongVersion = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(versionDatabase)
                        .findReviewForManager(1002, 501)
        );
        assertEquals("Submission answer version is invalid: 30",
                wrongVersion.getMessage());
    }

    @Test
    public void studentPublishedSummaryMapsOnlyOwnPublishedRowsNewestFirst() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan plan = database
                .plan(STUDENT_LIST_MARKER)
                .queryRows(
                        publishedSummaryRow(502, PUBLISHED.plusDays(1)),
                        publishedSummaryRow(501, PUBLISHED)
                );

        List<PublishedGradeSummaryDTO> grades =
                new ExamSubmissionRepository(database)
                        .findPublishedSummariesForStudent(1001);

        assertEquals(List.of(502, 501), grades.stream()
                .map(PublishedGradeSummaryDTO::getSubmissionId).toList());
        PublishedGradeSummaryDTO grade = grades.get(0);
        assertEquals(81, grade.getExecutionId());
        assertEquals(40, grade.getExamId());
        assertEquals(3, grade.getExamVersionNo());
        assertEquals("Historical Algebra Final", grade.getExamTitle());
        assertEquals("Algebra", grade.getCourseName());
        assertEquals(new BigDecimal("65.00"), grade.getFinalScore());
        assertEquals(SUBMITTED, grade.getSubmittedAt());
        assertEquals(PUBLISHED.plusDays(1), grade.getPublishedAt());
        assertThrows(UnsupportedOperationException.class, grades::clear);
        assertEquals(Map.of(1, 1001), plan.queryExecutions.get(0));
        assertStudentAuthorizationAndPrivacy(plan.sql, false);
        assertReadOnly(database, plan.sql);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController deniedDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        deniedDatabase.plan(STUDENT_LIST_MARKER).queryRows();
        assertTrue(new ExamSubmissionRepository(deniedDatabase)
                .findPublishedSummariesForStudent(1002).isEmpty());
    }

    @Test
    public void studentPublishedDetailMapsPermittedFieldsAndHidesAllOthers() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan plan = database
                .plan(STUDENT_DETAIL_MARKER)
                .queryRows(publishedDetailRow());

        PublishedGradeDTO grade = new ExamSubmissionRepository(database)
                .findPublishedGradeForStudent(1001, 501)
                .orElseThrow();

        assertEquals(501, grade.getSubmissionId());
        assertEquals(81, grade.getExecutionId());
        assertEquals(40, grade.getExamId());
        assertEquals(3, grade.getExamVersionNo());
        assertEquals("Historical Algebra Final", grade.getExamTitle());
        assertEquals("Algebra", grade.getCourseName());
        assertEquals(SubmissionStatus.PUBLISHED, grade.getStatus());
        assertEquals(new BigDecimal("65.00"), grade.getFinalScore());
        assertEquals("Strong work", grade.getTeacherFeedback());
        assertEquals(SUBMITTED, grade.getSubmittedAt());
        assertEquals(REVIEWED, grade.getReviewedAt());
        assertEquals(PUBLISHED, grade.getPublishedAt());
        assertEquals(Map.of(1, 1001, 2, 501), plan.queryExecutions.get(0));
        assertStudentAuthorizationAndPrivacy(plan.sql, true);
        assertReadOnly(database, plan.sql);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController hiddenDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        hiddenDatabase.plan(STUDENT_DETAIL_MARKER).queryRows();
        assertTrue(new ExamSubmissionRepository(hiddenDatabase)
                .findPublishedGradeForStudent(1005, 501).isEmpty());
    }

    @Test
    public void allProjectionSqlFailuresKeepStableMessagesAndCauses() {
        assertSqlFailure(
                MANAGER_LIST_MARKER,
                "Failed to load execution submissions",
                repository -> repository.findSummariesForManager(1002, 81)
        );
        assertSqlFailure(
                MANAGER_DETAIL_MARKER,
                "Failed to load submission for review",
                repository -> repository.findReviewForManager(1002, 501)
        );
        assertSqlFailure(
                STUDENT_LIST_MARKER,
                "Failed to load published grades",
                repository -> repository.findPublishedSummariesForStudent(1001)
        );
        assertSqlFailure(
                STUDENT_DETAIL_MARKER,
                "Failed to load published grade",
                repository -> repository.findPublishedGradeForStudent(1001, 501)
        );
    }

    private static void assertSummary(ExecutionSubmissionSummaryDTO summary,
                                      int submissionId, int studentId,
                                      String studentName) {
        assertEquals(submissionId, summary.getSubmissionId());
        assertEquals(81, summary.getExecutionId());
        assertEquals(40, summary.getExamId());
        assertEquals(3, summary.getExamVersionNo());
        assertEquals("Historical Algebra Final", summary.getExamTitle());
        assertEquals(studentId, summary.getStudentUserId());
        assertEquals(studentName, summary.getStudentName());
        assertEquals(SubmissionStatus.SUBMITTED, summary.getStatus());
        assertEquals(new BigDecimal("60.00"), summary.getAutomaticScore());
        assertEquals(new BigDecimal("65.00"), summary.getFinalScore());
        assertEquals(STARTED, summary.getStartedAt());
        assertEquals(SUBMITTED, summary.getSubmittedAt());
        assertEquals(REVIEWED, summary.getReviewedAt());
        assertNull(summary.getPublishedAt());
    }

    private static void assertManagerAuthorizationAndExactVersion(String sql) {
        String normalized = normalized(sql);
        assertTrue(normalized.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(normalized.contains("MANAGER.ROLE = 'TEACHER'"));
        assertTrue(normalized.contains("FROM TEACHER_COURSES"));
        assertTrue(normalized.contains("MANAGER.ROLE = 'COORDINATOR'"));
        assertTrue(normalized.contains("FROM SUBJECT_COORDINATORS"));
        assertTrue(normalized.contains(
                "VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"
        ));
        assertFalse(normalized.contains("CURRENT_VERSION_NO"));
        assertFalse(normalized.contains("EXECUTION.CREATED_BY_USER_ID ="));
    }

    private static void assertExactAnswerVersionWithoutCorrectOption(String sql) {
        String normalized = normalized(sql);
        assertTrue(normalized.contains(
                "QUESTION_VERSION.VERSION_NO = SELECTION.QUESTION_VERSION_NO"
        ));
        assertTrue(normalized.contains(
                "SELECTED_OPTION.VERSION_NO = SELECTION.QUESTION_VERSION_NO"
        ));
        assertTrue(normalized.contains("ORDER BY SELECTION.ORDER_NUMBER ASC"));
        assertFalse(normalized.contains("CORRECT_OPTION"));
        assertFalse(normalized.contains("IS_CORRECT = 1"));
    }

    private static void assertStudentAuthorizationAndPrivacy(String sql,
                                                              boolean detail) {
        String normalized = normalized(sql);
        assertTrue(normalized.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(normalized.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(normalized.contains(
                "SUBMISSION.STUDENT_USER_ID = STUDENT.USER_ID"
        ));
        assertTrue(normalized.contains("SUBMISSION.STATUS = 'PUBLISHED'"));
        assertTrue(normalized.contains("SUBMISSION.FINAL_SCORE IS NOT NULL"));
        assertTrue(normalized.contains("SUBMISSION.PUBLISHED_AT IS NOT NULL"));
        assertTrue(normalized.contains(
                "VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"
        ));
        assertFalse(normalized.contains("CURRENT_VERSION_NO"));
        for (String forbidden : List.of(
                "AUTOMATIC_SCORE", "MANUAL_CHANGE_REASON",
                "REVIEWED_BY_USER_ID", "PUBLISHED_BY_USER_ID", "IS_CORRECT",
                "SCORE_RECEIVED", "CORRECT_OPTION", "PASSWORD_HASH",
                "IDENTITY_NUMBER_HASH"
        )) {
            assertFalse("Student query exposes " + forbidden,
                    normalized.contains(forbidden));
        }
        if (!detail) {
            assertFalse(normalized.contains("TEACHER_FEEDBACK"));
            assertFalse(normalized.contains("REVIEWED_AT"));
        }
    }

    private static void assertReadOnly(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database,
            String... sqlStatements
    ) {
        assertEquals(0, database.autoCommitSetCalls);
        assertEquals(0, database.commitCount);
        assertEquals(0, database.rollbackCount);
        for (String sql : sqlStatements) {
            String normalized = normalized(sql);
            assertFalse(normalized.contains("FOR UPDATE"));
            assertFalse(normalized.startsWith("UPDATE "));
            assertFalse(normalized.startsWith("INSERT "));
            assertFalse(normalized.startsWith("DELETE "));
        }
    }

    private static void assertMalformedAnswers(Map<Object, Object> first,
                                               Map<Object, Object> second) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                reviewDatabase(first, second);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(database)
                        .findReviewForManager(1002, 501)
        );
    }

    @SafeVarargs
    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController reviewDatabase(
            Map<Object, Object>... answers
    ) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(MANAGER_DETAIL_MARKER).queryRows(reviewHeaderRow("SUBMITTED"));
        database.plan(ANSWER_REVIEW_MARKER).queryRows(answers);
        return database;
    }

    private static void assertSqlFailure(String marker, String message,
                                         ProjectionCall call) {
        SQLException cause = new SQLException("database unavailable");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(marker).queryFailure(cause);
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> call.execute(new ExamSubmissionRepository(database))
        );
        assertEquals(message, thrown.getMessage());
        assertSame(cause, thrown.getCause());
        assertReadOnly(database, database.plans.get(0).sql);
    }

    private static Map<Object, Object> managerSummaryRow(
            int submissionId, int studentId, String studentName, int examVersion
    ) {
        return row(
                "submission_id", submissionId,
                "execution_id", 81,
                "exam_id", 40,
                "exam_version_no", examVersion,
                "exam_title", "Historical Algebra Final",
                "student_user_id", studentId,
                "student_name", studentName,
                "status", "SUBMITTED",
                "automatic_score", new BigDecimal("60.00"),
                "final_score", new BigDecimal("65.00"),
                "started_at", STARTED,
                "submitted_at", SUBMITTED,
                "reviewed_at", REVIEWED,
                "published_at", null
        );
    }

    private static Map<Object, Object> reviewHeaderRow(String status) {
        return row(
                "submission_id", 501,
                "execution_id", 81,
                "exam_id", 40,
                "exam_version_no", 3,
                "exam_title", "Historical Algebra Final",
                "student_user_id", 1001,
                "student_name", "Development Student",
                "status", status,
                "automatic_score", new BigDecimal("60.00"),
                "final_score", new BigDecimal("65.00"),
                "teacher_feedback", "Strong work",
                "manual_change_reason", "Accepted alternate method",
                "reviewed_by_user_id", 1002,
                "started_at", STARTED,
                "submitted_at", SUBMITTED,
                "reviewed_at", REVIEWED,
                "updated_at", UPDATED,
                "published_by_user_id", null,
                "published_at", null
        );
    }

    private static Map<Object, Object> answeredReviewRow(
            int questionId, int versionNo, int orderNumber, int answerId,
            int answeredVersionNo, String selectedText
    ) {
        return row(
                "question_id", questionId,
                "question_version_no", versionNo,
                "order_number", orderNumber,
                "question_content", "Historical question " + questionId,
                "answer_id", answerId,
                "answered_question_version_no", answeredVersionNo,
                "selected_option_text", selectedText,
                "is_correct", false,
                "awarded_score", new BigDecimal("0.00"),
                "maximum_score", new BigDecimal("50.00")
        );
    }

    private static Map<Object, Object> unansweredReviewRow(
            int questionId, int versionNo, int orderNumber
    ) {
        return row(
                "question_id", questionId,
                "question_version_no", versionNo,
                "order_number", orderNumber,
                "question_content", "Historical question " + questionId,
                "answer_id", null,
                "answered_question_version_no", null,
                "selected_option_text", null,
                "is_correct", null,
                "awarded_score", null,
                "maximum_score", new BigDecimal("50.00")
        );
    }

    private static Map<Object, Object> publishedSummaryRow(
            int submissionId, LocalDateTime publishedAt
    ) {
        return row(
                "submission_id", submissionId,
                "execution_id", 81,
                "exam_id", 40,
                "exam_version_no", 3,
                "exam_title", "Historical Algebra Final",
                "course_name", "Algebra",
                "final_score", new BigDecimal("65.00"),
                "submitted_at", SUBMITTED,
                "published_at", publishedAt
        );
    }

    private static Map<Object, Object> publishedDetailRow() {
        Map<Object, Object> grade = publishedSummaryRow(501, PUBLISHED);
        grade.put("status", "PUBLISHED");
        grade.put("teacher_feedback", "Strong work");
        grade.put("reviewed_at", REVIEWED);
        return grade;
    }

    @FunctionalInterface
    private interface ProjectionCall {
        Object execute(ExamSubmissionRepository repository);
    }
}
