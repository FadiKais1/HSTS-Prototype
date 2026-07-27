package hsts.server.repository;

import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamRepositoryEntityReadTest {
    private static final String ENTITY_HEADER = "LEFT JOIN exam_versions ev";
    private static final String ENTITY_SELECTIONS = "LEFT JOIN question_versions qv";

    @Test
    public void teacherReadHydratesCompleteAggregateFromExactSelectedVersions() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        ExamRepositoryJdbcTestSupport.StatementPlan header = database.plan(ENTITY_HEADER)
                .queryRows(examRow());
        ExamRepositoryJdbcTestSupport.StatementPlan selections =
                database.plan(ENTITY_SELECTIONS).queryRows(selectionRows());

        Exam exam = new ExamRepository(database)
                .findCurrentEntityForTeacher(1002, 45)
                .orElseThrow();

        assertEquals(Map.of(1, 1002, 2, 45, 3, 1002),
                header.queryExecutions.get(0));
        assertEquals(Map.of(1, 45, 2, 3), selections.queryExecutions.get(0));
        assertEquals(45, exam.getExamId());
        assertEquals("ABC123", exam.getExamCode());
        assertEquals(7, exam.getCourseId());
        assertEquals(1002, exam.getCreatedByUserId());
        assertEquals(3, exam.getCurrentVersionNo());
        assertEquals("Midterm", exam.getTitle());
        assertEquals(90, exam.getDurationMinutes());
        assertEquals("Teacher notes", exam.getTeacherNotes());
        assertEquals("Read carefully", exam.getStudentInstructions());
        assertEquals(ExamStatus.REJECTED, exam.getStatus());
        assertEquals(new BigDecimal("100.00"), exam.getTotalScoreValue());
        assertEquals(LocalDateTime.of(2026, 7, 20, 8, 0), exam.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 7, 23, 8, 0), exam.getUpdatedAt());
        assertEquals(Integer.valueOf(1003), exam.getReviewedByUserId());
        assertEquals("Revise wording", exam.getRejectionReason());

        List<ExamQuestion> questions = exam.getExamQuestions();
        assertEquals(List.of(1, 2), questions.stream()
                .map(ExamQuestion::getOrderNumber).toList());
        assertEquals(List.of(17, 18), questions.stream()
                .map(ExamQuestion::getQuestionId).toList());
        assertEquals(List.of(4, 2), questions.stream()
                .map(ExamQuestion::getQuestionVersionNo).toList());
        assertEquals("Historical 17", questions.get(0).getQuestion().getContent());
        assertEquals("Historical 18", questions.get(1).getQuestion().getContent());
        assertEquals(QuestionStatus.ACTIVE,
                questions.get(0).getQuestion().getQuestionStatus());
        List<AnswerOption> options = questions.get(0).getQuestion().getAnswerOptions();
        assertEquals(List.of(1, 2, 3, 4), options.stream()
                .map(AnswerOption::getOptionId).toList());
        assertTrue(options.get(2).isCorrect());

        questions.get(0).updateScore(BigDecimal.ONE);
        options.get(2).markAsIncorrect();
        assertEquals(new BigDecimal("40.00"),
                exam.getExamQuestions().get(0).getScoreValue());
        assertTrue(exam.getExamQuestions().get(0).getQuestion()
                .getAnswerOptions().get(2).isCorrect());

        String snapshotSql = normalized(selections.sql);
        assertTrue(snapshotSql.contains("QV.VERSION_NO = EVQ.QUESTION_VERSION_NO"));
        assertTrue(snapshotSql.contains("AO.VERSION_NO = EVQ.QUESTION_VERSION_NO"));
        assertFalse(snapshotSql.contains("CURRENT_VERSION_NO"));
        assertTrue(snapshotSql.contains(
                "ORDER BY EVQ.ORDER_NUMBER ASC, AO.OPTION_NUMBER ASC"
        ));
    }

    @Test
    public void coordinatorReadUsesSubjectScopeAndMissingExamReturnsEmpty() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController coordinatorDatabase = database();
        ExamRepositoryJdbcTestSupport.StatementPlan header =
                coordinatorDatabase.plan(ENTITY_HEADER).queryRows(examRow());
        coordinatorDatabase.plan(ENTITY_SELECTIONS).queryRows(selectionRows());

        Exam exam = new ExamRepository(coordinatorDatabase)
                .findCurrentEntityForCoordinator(1003, 45)
                .orElseThrow();

        assertEquals(45, exam.getExamId());
        assertEquals(Map.of(1, 1003, 2, 45), header.queryExecutions.get(0));
        String sql = normalized(header.sql);
        assertTrue(sql.contains("JOIN SUBJECT_COORDINATORS SC"));
        assertTrue(sql.contains("SC.COORDINATOR_USER_ID = ?"));
        assertFalse(sql.contains("E.CREATED_BY_USER_ID = ?"));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingDatabase = database();
        missingDatabase.plan(ENTITY_HEADER).queryRows();
        assertTrue(new ExamRepository(missingDatabase)
                .findCurrentEntityForTeacher(1002, 99).isEmpty());
    }

    @Test
    public void malformedCurrentOrSelectedVersionIsRejected() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingCurrent = database();
        Map<Object, Object> missingVersion = examRow();
        missingVersion.put("version_no", null);
        missingCurrent.plan(ENTITY_HEADER).queryRows(missingVersion);
        IllegalArgumentException currentFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(missingCurrent)
                        .findCurrentEntityForTeacher(1002, 45)
        );
        assertEquals("Current exam version is missing: 45", currentFailure.getMessage());

        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingSnapshot = database();
        missingSnapshot.plan(ENTITY_HEADER).queryRows(examRow());
        Map<Object, Object>[] rows = selectionRows();
        rows[0].put("snapshot_version_no", null);
        missingSnapshot.plan(ENTITY_SELECTIONS).queryRows(rows);
        IllegalArgumentException snapshotFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(missingSnapshot)
                        .findCurrentEntityForTeacher(1002, 45)
        );
        assertEquals(
                "Selected question version is missing: 17 version 4",
                snapshotFailure.getMessage()
        );
    }

    @Test
    public void malformedOptionsEnumsAndTotalsAreRejected() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingOption = database();
        missingOption.plan(ENTITY_HEADER).queryRows(examRow());
        Map<Object, Object>[] threeOptions = selectionRows();
        List<Map<Object, Object>> shortened = new ArrayList<>(List.of(threeOptions));
        shortened.remove(3);
        missingOption.plan(ENTITY_SELECTIONS)
                .queryRows(shortened.toArray(Map[]::new));
        IllegalArgumentException optionFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(missingOption)
                        .findCurrentEntityForTeacher(1002, 45)
        );
        assertEquals(
                "Question must contain exactly four answer options: 17",
                optionFailure.getMessage()
        );

        ExamRepositoryJdbcTestSupport.FakeDatabaseController invalidEnum = database();
        invalidEnum.plan(ENTITY_HEADER).queryRows(examRow());
        Map<Object, Object>[] invalidRows = selectionRows();
        invalidRows[0].put("difficulty", "IMPOSSIBLE");
        invalidEnum.plan(ENTITY_SELECTIONS).queryRows(invalidRows);
        IllegalArgumentException enumFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(invalidEnum)
                        .findCurrentEntityForTeacher(1002, 45)
        );
        assertEquals("Question difficulty is invalid: 17", enumFailure.getMessage());

        ExamRepositoryJdbcTestSupport.FakeDatabaseController wrongTotal = database();
        Map<Object, Object> wrongTotalRow = examRow();
        wrongTotalRow.put("total_score", new BigDecimal("99.00"));
        wrongTotal.plan(ENTITY_HEADER).queryRows(wrongTotalRow);
        wrongTotal.plan(ENTITY_SELECTIONS).queryRows(selectionRows());
        IllegalArgumentException totalFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(wrongTotal)
                        .findCurrentEntityForTeacher(1002, 45)
        );
        assertEquals(
                "Exam total score does not match selections: 45",
                totalFailure.getMessage()
        );
    }

    @Test
    public void jdbcFailuresKeepEntityReadMessagesAndOriginalCauses() {
        SQLException teacherCause = new SQLException("teacher read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController teacherDatabase = database();
        teacherDatabase.connectionFailure = teacherCause;
        IllegalStateException teacherFailure = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(teacherDatabase)
                        .findCurrentEntityForTeacher(1002, 45)
        );
        assertEquals("Failed to load teacher exam entity", teacherFailure.getMessage());
        assertSame(teacherCause, teacherFailure.getCause());

        SQLException coordinatorCause = new SQLException("coordinator read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController coordinatorDatabase = database();
        coordinatorDatabase.connectionFailure = coordinatorCause;
        IllegalStateException coordinatorFailure = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(coordinatorDatabase)
                        .findCurrentEntityForCoordinator(1003, 45)
        );
        assertEquals("Failed to load coordinator exam entity", coordinatorFailure.getMessage());
        assertSame(coordinatorCause, coordinatorFailure.getCause());
    }

    private static Map<Object, Object> examRow() {
        return row(
                "exam_id", 45,
                "exam_code", "ABC123",
                "course_id", 7,
                "created_by_user_id", 1002,
                "exam_created_at", LocalDateTime.of(2026, 7, 20, 8, 0),
                "exam_updated_at", LocalDateTime.of(2026, 7, 21, 8, 0),
                "version_no", 3,
                "title", "Midterm",
                "duration_minutes", 90,
                "teacher_notes", "Teacher notes",
                "student_instructions", "Read carefully",
                "total_score", new BigDecimal("100.00"),
                "status", "REJECTED",
                "version_created_at", LocalDateTime.of(2026, 7, 21, 8, 0),
                "submitted_at", LocalDateTime.of(2026, 7, 22, 8, 0),
                "reviewed_by_user_id", 1003,
                "reviewed_at", LocalDateTime.of(2026, 7, 23, 8, 0),
                "rejection_reason", "Revise wording"
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object>[] selectionRows() {
        List<Map<Object, Object>> rows = new ArrayList<>();
        addQuestionRows(rows, 17, 4, 1, new BigDecimal("40.00"), "Historical 17");
        addQuestionRows(rows, 18, 2, 2, new BigDecimal("60.00"), "Historical 18");
        return rows.toArray(Map[]::new);
    }

    private static void addQuestionRows(List<Map<Object, Object>> rows, int questionId,
                                        int versionNo, int orderNumber, BigDecimal score,
                                        String content) {
        for (int optionNumber = 1; optionNumber <= 4; optionNumber++) {
            rows.add(row(
                    "question_id", questionId,
                    "question_version_no", versionNo,
                    "order_number", orderNumber,
                    "score", score,
                    "snapshot_question_id", questionId,
                    "snapshot_version_no", versionNo,
                    "content", content,
                    "topic", "Algebra",
                    "question_type", "MULTIPLE_CHOICE",
                    "difficulty", "HARD",
                    "illustration_path", "historical.png",
                    "correct_option_number", 3,
                    "question_version_created_at", LocalDateTime.of(2026, 7, 10, 8, 0),
                    "question_status", "ACTIVE",
                    "option_number", optionNumber,
                    "option_text", "Option " + optionNumber
            ));
        }
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController database() {
        return new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
