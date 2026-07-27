package hsts.server.repository;

import hsts.common.ExamDTO;
import hsts.common.ExamQuestionDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.type.ExamStatus;
import org.junit.Test;

import java.math.BigDecimal;
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

public class ExamRepositoryReadTest {
    @Test
    public void teacherListScopesCreatorAndAssignmentAndMapsNewestFirst() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan list = database.plan(
                "ORDER BY e.created_at DESC, e.exam_id DESC"
        ).queryRows(
                summaryRow(22, "ABC123", 7, 1002, 3, ExamStatus.REJECTED,
                        LocalDateTime.of(2026, 7, 27, 12, 0)),
                summaryRow(19, "XYZ789", 7, 1002, 1, ExamStatus.DRAFT,
                        LocalDateTime.of(2026, 7, 20, 8, 0))
        );

        List<ExamSummaryDTO> exams = new ExamRepository(database)
                .findCreatedByTeacher(1002);

        assertEquals(2, exams.size());
        assertSummary(exams.get(0));
        assertEquals(22, exams.get(0).getExamId());
        assertEquals(19, exams.get(1).getExamId());
        assertEquals(Map.of(1, 1002, 2, 1002), list.queryExecutions.get(0));

        String sql = normalized(list.sql);
        assertTrue(sql.contains("JOIN TEACHER_COURSES TC"));
        assertTrue(sql.contains("TC.TEACHER_USER_ID = ?"));
        assertTrue(sql.contains("WHERE E.CREATED_BY_USER_ID = ?"));
        assertTrue(sql.contains("EV.VERSION_NO = E.CURRENT_VERSION_NO"));
        assertNoWrites(database);
    }

    @Test
    public void coordinatorListScopesSubjectShowsSelfAuthoredPendingAndOrdersOldestFirst() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan list = database.plan(
                "ORDER BY ev.submitted_at ASC, e.exam_id ASC"
        ).queryRows(
                summaryRow(30, "COORD1", 7, 1003, 1,
                        ExamStatus.PENDING_APPROVAL, LocalDateTime.of(2026, 7, 21, 9, 0)),
                summaryRow(31, "TEACH1", 7, 1002, 2,
                        ExamStatus.PENDING_APPROVAL, LocalDateTime.of(2026, 7, 22, 9, 0))
        );

        List<ExamSummaryDTO> exams = new ExamRepository(database)
                .findPendingForCoordinator(1003);

        assertEquals(List.of(30, 31), exams.stream().map(ExamSummaryDTO::getExamId).toList());
        assertEquals(1003, exams.get(0).getCreatedByUserId());
        assertEquals(ExamStatus.PENDING_APPROVAL, exams.get(0).getStatus());
        assertEquals(Map.of(1, 1003), list.queryExecutions.get(0));

        String sql = normalized(list.sql);
        assertTrue(sql.contains("JOIN SUBJECT_COORDINATORS SC"));
        assertTrue(sql.contains("SC.COORDINATOR_USER_ID = ?"));
        assertTrue(sql.contains("WHERE EV.STATUS = 'PENDING_APPROVAL'"));
        assertFalse(sql.contains("CREATED_BY_USER_ID <>"));
        assertNoWrites(database);
    }

    @Test
    public void teacherDetailMapsCompleteCurrentVersionAndHistoricalQuestionSnapshots() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan header = database.plan(
                "AND e.created_by_user_id = ?"
        ).queryRows(detailRow());
        ExamRepositoryJdbcTestSupport.StatementPlan snapshots = database.plan(
                "FROM exam_version_questions evq"
        ).queryRows(
                questionRow(17, 4, 1, "Historical first", "Old one"),
                questionRow(18, 2, 2, "Historical second", "Old two")
        );

        Optional<ExamDTO> result = new ExamRepository(database)
                .findByIdForTeacher(1002, 22);

        assertTrue(result.isPresent());
        ExamDTO exam = result.get();
        assertExam(exam);
        assertEquals(2, exam.getQuestions().size());
        assertQuestion(exam.getQuestions().get(0), 17, 4, 1, "Historical first", "Old one");
        assertQuestion(exam.getQuestions().get(1), 18, 2, 2, "Historical second", "Old two");
        assertEquals(Map.of(1, 1002, 2, 22, 3, 1002), header.queryExecutions.get(0));
        assertEquals(Map.of(1, 22, 2, 3), snapshots.queryExecutions.get(0));

        String headerSql = normalized(header.sql);
        assertTrue(headerSql.contains("JOIN TEACHER_COURSES TC"));
        assertTrue(headerSql.contains("EV.VERSION_NO = E.CURRENT_VERSION_NO"));

        String snapshotSql = normalized(snapshots.sql);
        assertTrue(snapshotSql.contains("JOIN QUESTION_VERSIONS QV"));
        assertTrue(snapshotSql.contains("QV.VERSION_NO = EVQ.QUESTION_VERSION_NO"));
        assertTrue(snapshotSql.contains("OPTION_1.VERSION_NO = QV.VERSION_NO"));
        assertTrue(snapshotSql.contains("OPTION_4.OPTION_TEXT AS ANSWER_OPTION_4"));
        assertTrue(snapshotSql.contains("ORDER BY EVQ.ORDER_NUMBER ASC"));
        assertFalse(snapshotSql.contains("Q.ANSWER_OPTION_"));
        assertNoWrites(database);
    }

    @Test
    public void coordinatorDetailUsesSubjectAssignmentRatherThanCreatorOrReviewer() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan header = database.plan(
                "SC.COORDINATOR_USER_ID = ?"
        ).queryRows(detailRow());
        database.plan("FROM exam_version_questions evq").queryRows();

        ExamDTO exam = new ExamRepository(database)
                .findByIdForCoordinator(1003, 22)
                .orElseThrow();

        assertEquals(22, exam.getExamId());
        assertEquals(Map.of(1, 1003, 2, 22), header.queryExecutions.get(0));
        String sql = normalized(header.sql);
        assertTrue(sql.contains("JOIN SUBJECT_COORDINATORS SC"));
        assertFalse(sql.contains("E.CREATED_BY_USER_ID = ?"));
        assertFalse(sql.contains("EV.REVIEWED_BY_USER_ID = ?"));
        assertNoWrites(database);
    }

    @Test
    public void emptyAndInaccessibleReadsReturnEmptyResults() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan("ORDER BY e.created_at DESC, e.exam_id DESC").queryRows();
        database.plan("ORDER BY ev.submitted_at ASC, e.exam_id ASC").queryRows();
        database.plan("AND e.created_by_user_id = ?").queryRows();
        database.plan("SC.COORDINATOR_USER_ID = ?").queryRows();
        ExamRepository repository = new ExamRepository(database);

        assertTrue(repository.findCreatedByTeacher(50).isEmpty());
        assertTrue(repository.findPendingForCoordinator(60).isEmpty());
        assertTrue(repository.findByIdForTeacher(50, 99).isEmpty());
        assertTrue(repository.findByIdForCoordinator(60, 99).isEmpty());
        assertNoWrites(database);
    }

    @Test
    public void eachReadWrapsSqlFailureWithItsExactMessageAndOriginalCause() {
        assertReadFailure("Failed to load teacher exams",
                repository -> repository.findCreatedByTeacher(1));
        assertReadFailure("Failed to load pending exams",
                repository -> repository.findPendingForCoordinator(1));
        assertReadFailure("Failed to load teacher exam",
                repository -> repository.findByIdForTeacher(1, 2));
        assertReadFailure("Failed to load coordinator exam",
                repository -> repository.findByIdForCoordinator(1, 2));
    }

    private static void assertReadFailure(String expectedMessage, RepositoryCall call) {
        SQLException failure = new SQLException("read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.connectionFailure = failure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> call.invoke(new ExamRepository(database))
        );
        assertEquals(expectedMessage, thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }

    private static Map<Object, Object> summaryRow(int examId, String examCode, int courseId,
                                                   int creatorId, int versionNo,
                                                   ExamStatus status, LocalDateTime createdAt) {
        return row(
                "exam_id", examId,
                "exam_code", examCode,
                "course_id", courseId,
                "course_name", "Mathematics",
                "subject_id", 2,
                "subject_name", "Math",
                "created_by_user_id", creatorId,
                "creator_name", creatorId == 1003
                        ? "Development Coordinator" : "Development Teacher",
                "version_no", versionNo,
                "title", "Midterm",
                "duration_minutes", 90,
                "total_score", new BigDecimal("100.00"),
                "status", status.name(),
                "created_at", createdAt,
                "submitted_at", LocalDateTime.of(2026, 7, 25, 10, 0),
                "reviewed_at", LocalDateTime.of(2026, 7, 26, 10, 0),
                "rejection_reason", status == ExamStatus.REJECTED ? "Revise wording" : null
        );
    }

    private static Map<Object, Object> detailRow() {
        return row(
                "exam_id", 22,
                "exam_code", "ABC123",
                "course_id", 7,
                "course_name", "Mathematics",
                "subject_id", 2,
                "subject_name", "Math",
                "created_by_user_id", 1002,
                "creator_name", "Development Teacher",
                "version_no", 3,
                "title", "Midterm",
                "duration_minutes", 90,
                "teacher_notes", "Teacher only",
                "student_instructions", "Read carefully",
                "total_score", new BigDecimal("100.00"),
                "status", "REJECTED",
                "created_at", LocalDateTime.of(2026, 7, 20, 8, 0),
                "submitted_at", LocalDateTime.of(2026, 7, 21, 8, 0),
                "reviewed_by_user_id", 1003,
                "reviewer_name", "Development Coordinator",
                "reviewed_at", LocalDateTime.of(2026, 7, 22, 8, 0),
                "rejection_reason", "Revise wording"
        );
    }

    private static Map<Object, Object> questionRow(int questionId, int versionNo,
                                                    int orderNumber, String content,
                                                    String optionOne) {
        return row(
                "question_id", questionId,
                "question_version_no", versionNo,
                "order_number", orderNumber,
                "score", new BigDecimal(orderNumber == 1 ? "40.00" : "60.00"),
                "content", content,
                "topic", "Algebra",
                "difficulty", "HARD",
                "illustration_path", "old.png",
                "answer_option_1", optionOne,
                "answer_option_2", "Two",
                "answer_option_3", "Three",
                "answer_option_4", "Four",
                "correct_option_number", 3
        );
    }

    private static void assertSummary(ExamSummaryDTO exam) {
        assertEquals("ABC123", exam.getExamCode());
        assertEquals(7, exam.getCourseId());
        assertEquals("Mathematics", exam.getCourseName());
        assertEquals(2, exam.getSubjectId());
        assertEquals("Math", exam.getSubjectName());
        assertEquals(1002, exam.getCreatedByUserId());
        assertEquals("Development Teacher", exam.getCreatorName());
        assertEquals(3, exam.getVersionNo());
        assertEquals("Midterm", exam.getTitle());
        assertEquals(90, exam.getDurationMinutes());
        assertEquals(100.0, exam.getTotalScore(), 0.0);
        assertEquals(ExamStatus.REJECTED, exam.getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 27, 12, 0), exam.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 7, 25, 10, 0), exam.getSubmittedAt());
        assertEquals(LocalDateTime.of(2026, 7, 26, 10, 0), exam.getReviewedAt());
        assertEquals("Revise wording", exam.getRejectionReason());
    }

    private static void assertExam(ExamDTO exam) {
        assertEquals(22, exam.getExamId());
        assertEquals("ABC123", exam.getExamCode());
        assertEquals(7, exam.getCourseId());
        assertEquals("Mathematics", exam.getCourseName());
        assertEquals(2, exam.getSubjectId());
        assertEquals("Math", exam.getSubjectName());
        assertEquals(1002, exam.getCreatedByUserId());
        assertEquals("Development Teacher", exam.getCreatorName());
        assertEquals(3, exam.getVersionNo());
        assertEquals("Midterm", exam.getTitle());
        assertEquals(90, exam.getDurationMinutes());
        assertEquals("Teacher only", exam.getTeacherNotes());
        assertEquals("Read carefully", exam.getStudentInstructions());
        assertEquals(100.0, exam.getTotalScore(), 0.0);
        assertEquals(ExamStatus.REJECTED, exam.getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 20, 8, 0), exam.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 7, 21, 8, 0), exam.getSubmittedAt());
        assertEquals(Integer.valueOf(1003), exam.getReviewedByUserId());
        assertEquals("Development Coordinator", exam.getReviewerName());
        assertEquals(LocalDateTime.of(2026, 7, 22, 8, 0), exam.getReviewedAt());
        assertEquals("Revise wording", exam.getRejectionReason());
    }

    private static void assertQuestion(ExamQuestionDTO question, int questionId,
                                       int versionNo, int orderNumber, String content,
                                       String optionOne) {
        assertEquals(questionId, question.getQuestionId());
        assertEquals(versionNo, question.getQuestionVersionNo());
        assertEquals(orderNumber, question.getOrderNumber());
        assertEquals(orderNumber == 1 ? 40.0 : 60.0, question.getScore(), 0.0);
        assertEquals(content, question.getContent());
        assertEquals("Algebra", question.getTopic());
        assertEquals("HARD", question.getDifficulty());
        assertEquals("old.png", question.getIllustrationPath());
        assertEquals(optionOne, question.getAnswerOption1());
        assertEquals("Two", question.getAnswerOption2());
        assertEquals("Three", question.getAnswerOption3());
        assertEquals("Four", question.getAnswerOption4());
        assertEquals(3, question.getCorrectOptionNumber());
    }

    private static void assertNoWrites(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database) {
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }

    @FunctionalInterface
    private interface RepositoryCall {
        void invoke(ExamRepository repository);
    }
}
