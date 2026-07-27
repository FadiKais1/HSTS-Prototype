package hsts.server.repository;

import hsts.common.type.ExamStatus;
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

public class ExamRepositoryExactVersionReadTest {
    private static final String EXACT_HEADER = "WHERE e.exam_id = ?";
    private static final String ENTITY_SELECTIONS = "LEFT JOIN question_versions qv";

    @Test
    public void exactHistoricalVersionIgnoresNewerLogicalAndQuestionPointers() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        ExamRepositoryJdbcTestSupport.StatementPlan header = database.plan(EXACT_HEADER)
                .queryRows(approvedHistoricalVersionRow());
        ExamRepositoryJdbcTestSupport.StatementPlan selections =
                database.plan(ENTITY_SELECTIONS).queryRows(selectionRows());

        Exam exam = new ExamRepository(database).findEntityVersion(45, 2).orElseThrow();

        assertEquals(Map.of(1, 45, 2, 2), header.queryExecutions.get(0));
        assertEquals(Map.of(1, 45, 2, 2), selections.queryExecutions.get(0));
        assertEquals(45, exam.getExamId());
        assertEquals("ABC123", exam.getExamCode());
        assertEquals(7, exam.getCourseId());
        assertEquals(1002, exam.getCreatedByUserId());
        assertEquals(2, exam.getCurrentVersionNo());
        assertEquals("Approved historical version", exam.getTitle());
        assertEquals(75, exam.getDurationMinutes());
        assertEquals(ExamStatus.APPROVED, exam.getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 21, 8, 0), exam.getSubmittedAt());
        assertEquals(Integer.valueOf(1003), exam.getReviewedByUserId());
        assertEquals(LocalDateTime.of(2026, 7, 22, 8, 0), exam.getReviewedAt());
        assertEquals(LocalDateTime.of(2026, 7, 22, 8, 0), exam.getUpdatedAt());
        assertEquals(new BigDecimal("100.00"), exam.getTotalScoreValue());

        List<ExamQuestion> questions = exam.getExamQuestions();
        assertEquals(List.of(1, 2), questions.stream()
                .map(ExamQuestion::getOrderNumber).toList());
        assertEquals(List.of(30, 10), questions.stream()
                .map(ExamQuestion::getQuestionId).toList());
        assertEquals(List.of(7, 2), questions.stream()
                .map(ExamQuestion::getQuestionVersionNo).toList());
        assertEquals(List.of(new BigDecimal("33.33"), new BigDecimal("66.67")),
                questions.stream().map(ExamQuestion::getScoreValue).toList());
        assertEquals("Historical question 30 version 7",
                questions.get(0).getQuestion().getContent());
        assertEquals("Historical question 10 version 2",
                questions.get(1).getQuestion().getContent());

        List<AnswerOption> options = questions.get(0).getQuestion().getAnswerOptions();
        assertEquals(List.of(1, 2, 3, 4), options.stream()
                .map(AnswerOption::getOptionId).toList());
        assertTrue(options.get(2).isCorrect());

        String headerSql = normalized(header.sql);
        assertTrue(headerSql.contains("JOIN EXAM_VERSIONS EV ON EV.EXAM_ID = E.EXAM_ID"));
        assertTrue(headerSql.contains("WHERE E.EXAM_ID = ? AND EV.VERSION_NO = ?"));
        assertFalse(headerSql.contains("CURRENT_VERSION_NO"));
        assertFalse(headerSql.contains("TEACHER_COURSES"));
        assertFalse(headerSql.contains("SUBJECT_COORDINATORS"));

        String snapshotSql = normalized(selections.sql);
        assertTrue(snapshotSql.contains("QV.VERSION_NO = EVQ.QUESTION_VERSION_NO"));
        assertTrue(snapshotSql.contains("AO.VERSION_NO = EVQ.QUESTION_VERSION_NO"));
        assertTrue(snapshotSql.contains(
                "ORDER BY EVQ.ORDER_NUMBER ASC, AO.OPTION_NUMBER ASC"
        ));
        assertFalse(snapshotSql.contains("Q.CURRENT_VERSION_NO"));
        assertNoStudentOrCredentialData(headerSql + " " + snapshotSql);
    }

    @Test
    public void exactAggregateAndNestedSnapshotsAreDefensive() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(EXACT_HEADER).queryRows(approvedHistoricalVersionRow());
        database.plan(ENTITY_SELECTIONS).queryRows(selectionRows());
        Exam exam = new ExamRepository(database).findEntityVersion(45, 2).orElseThrow();

        List<ExamQuestion> exposed = exam.getExamQuestions();
        exposed.get(0).updateScore(BigDecimal.ONE);
        exposed.get(0).getQuestion().getAnswerOptions().get(2).markAsIncorrect();

        List<ExamQuestion> reread = exam.getExamQuestions();
        assertEquals(new BigDecimal("33.33"), reread.get(0).getScoreValue());
        assertTrue(reread.get(0).getQuestion().getAnswerOptions().get(2).isCorrect());
        assertThrows(UnsupportedOperationException.class, () -> reread.add(reread.get(0)));
    }

    @Test
    public void missingLogicalExamOrRequestedVersionReturnsEmpty() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        ExamRepositoryJdbcTestSupport.StatementPlan header = database.plan(EXACT_HEADER)
                .queryRows()
                .queryRows();
        ExamRepository repository = new ExamRepository(database);

        assertTrue(repository.findEntityVersion(404, 2).isEmpty());
        assertTrue(repository.findEntityVersion(45, 99).isEmpty());
        assertEquals(Map.of(1, 404, 2, 2), header.queryExecutions.get(0));
        assertEquals(Map.of(1, 45, 2, 99), header.queryExecutions.get(1));
        assertEquals(1, database.plans.size());
    }

    @Test
    public void versionSpecificRejectedWorkflowMetadataMapsWithoutLogicalSubstitution() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        Map<Object, Object> rejected = approvedHistoricalVersionRow();
        rejected.put("status", "REJECTED");
        rejected.put("reviewed_at", LocalDateTime.of(2026, 7, 23, 9, 0));
        rejected.put("rejection_reason", "Revise historical wording");
        database.plan(EXACT_HEADER).queryRows(rejected);
        database.plan(ENTITY_SELECTIONS).queryRows(selectionRows());

        Exam exam = new ExamRepository(database).findEntityVersion(45, 2).orElseThrow();

        assertEquals(ExamStatus.REJECTED, exam.getStatus());
        assertEquals(Integer.valueOf(1003), exam.getReviewedByUserId());
        assertEquals(LocalDateTime.of(2026, 7, 23, 9, 0), exam.getReviewedAt());
        assertEquals("Revise historical wording", exam.getRejectionReason());
        assertEquals(LocalDateTime.of(2026, 7, 23, 9, 0), exam.getUpdatedAt());
    }

    @Test
    public void missingAndMalformedHistoricalSnapshotsAreRejectedDeterministically() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingSnapshot = database();
        missingSnapshot.plan(EXACT_HEADER).queryRows(approvedHistoricalVersionRow());
        Map<Object, Object>[] missingRows = selectionRows();
        missingRows[0].put("snapshot_version_no", null);
        missingSnapshot.plan(ENTITY_SELECTIONS).queryRows(missingRows);
        assertEquals(
                "Selected question version is missing: 30 version 7",
                assertThrows(IllegalArgumentException.class, () ->
                        new ExamRepository(missingSnapshot).findEntityVersion(45, 2)
                ).getMessage()
        );

        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingOption = database();
        missingOption.plan(EXACT_HEADER).queryRows(approvedHistoricalVersionRow());
        List<Map<Object, Object>> shortened = new ArrayList<>(List.of(selectionRows()));
        shortened.remove(3);
        missingOption.plan(ENTITY_SELECTIONS)
                .queryRows(shortened.toArray(Map[]::new));
        assertEquals(
                "Question must contain exactly four answer options: 30",
                assertThrows(IllegalArgumentException.class, () ->
                        new ExamRepository(missingOption).findEntityVersion(45, 2)
                ).getMessage()
        );
    }

    @Test
    public void duplicateHistoricalOrderAndSelectionIdentityAreRejected() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController duplicateOrder = database();
        duplicateOrder.plan(EXACT_HEADER).queryRows(approvedHistoricalVersionRow());
        duplicateOrder.plan(ENTITY_SELECTIONS).queryRows(selectionRows(1, 1, 30, 10));
        assertEquals(
                "Duplicate exam question order: 1",
                assertThrows(IllegalArgumentException.class, () ->
                        new ExamRepository(duplicateOrder).findEntityVersion(45, 2)
                ).getMessage()
        );

        ExamRepositoryJdbcTestSupport.FakeDatabaseController duplicateQuestion = database();
        duplicateQuestion.plan(EXACT_HEADER).queryRows(approvedHistoricalVersionRow());
        duplicateQuestion.plan(ENTITY_SELECTIONS).queryRows(selectionRows(1, 2, 30, 30));
        assertEquals(
                "Duplicate exam question: 30",
                assertThrows(IllegalArgumentException.class, () ->
                        new ExamRepository(duplicateQuestion).findEntityVersion(45, 2)
                ).getMessage()
        );
    }

    @Test
    public void exactReadValidatesIdentityAndPreservesJdbcCause() {
        ExamRepository repository = new ExamRepository(database());
        assertEquals("Exam ID must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> repository.findEntityVersion(0, 2)).getMessage());
        assertEquals("Exam version must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> repository.findEntityVersion(45, 0)).getMessage());

        SQLException cause = new SQLException("exact read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(EXACT_HEADER).queryFailure(cause);
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(database).findEntityVersion(45, 2)
        );
        assertEquals("Failed to load exact exam version", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    private static Map<Object, Object> approvedHistoricalVersionRow() {
        return row(
                "exam_id", 45,
                "exam_code", "ABC123",
                "course_id", 7,
                "created_by_user_id", 1002,
                "current_version_no", 5,
                "exam_created_at", LocalDateTime.of(2026, 7, 20, 8, 0),
                "exam_updated_at", LocalDateTime.of(2026, 8, 1, 8, 0),
                "version_no", 2,
                "title", "Approved historical version",
                "duration_minutes", 75,
                "teacher_notes", "Historical teacher notes",
                "student_instructions", "Historical instructions",
                "total_score", new BigDecimal("100.00"),
                "status", "APPROVED",
                "version_created_at", LocalDateTime.of(2026, 7, 20, 9, 0),
                "submitted_at", LocalDateTime.of(2026, 7, 21, 8, 0),
                "reviewed_by_user_id", 1003,
                "reviewed_at", LocalDateTime.of(2026, 7, 22, 8, 0),
                "rejection_reason", null
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object>[] selectionRows() {
        return selectionRows(1, 2, 30, 10);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object>[] selectionRows(
            int firstOrder,
            int secondOrder,
            int firstQuestionId,
            int secondQuestionId
    ) {
        List<Map<Object, Object>> rows = new ArrayList<>();
        addQuestionRows(
                rows,
                firstQuestionId,
                7,
                firstOrder,
                new BigDecimal("33.33"),
                "Historical question " + firstQuestionId + " version 7"
        );
        addQuestionRows(
                rows,
                secondQuestionId,
                2,
                secondOrder,
                new BigDecimal("66.67"),
                "Historical question " + secondQuestionId + " version 2"
        );
        return rows.toArray(Map[]::new);
    }

    private static void addQuestionRows(
            List<Map<Object, Object>> rows,
            int questionId,
            int versionNo,
            int orderNumber,
            BigDecimal score,
            String content
    ) {
        for (int optionNumber = 1; optionNumber <= 4; optionNumber++) {
            rows.add(row(
                    "question_id", questionId,
                    "question_version_no", versionNo,
                    "order_number", orderNumber,
                    "score", score,
                    "snapshot_question_id", questionId,
                    "snapshot_version_no", versionNo,
                    "current_question_version_no", versionNo + 3,
                    "content", content,
                    "topic", "Algebra",
                    "question_type", "MULTIPLE_CHOICE",
                    "difficulty", "HARD",
                    "illustration_path", "historical.png",
                    "correct_option_number", 3,
                    "question_version_created_at",
                    LocalDateTime.of(2026, 7, 10, 8, 0),
                    "question_status", "INACTIVE",
                    "option_number", optionNumber,
                    "option_text", "Historical option " + optionNumber
            ));
        }
    }

    private static void assertNoStudentOrCredentialData(String sql) {
        assertFalse(sql.contains("EXAM_SUBMISSIONS"));
        assertFalse(sql.contains("STUDENT_ANSWERS"));
        assertFalse(sql.contains("STUDENT_PROFILES"));
        assertFalse(sql.contains("STUDENT_COURSES"));
        assertFalse(sql.contains("PASSWORD"));
        assertFalse(sql.contains("IDENTITY_NUMBER"));
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController database() {
        return new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
