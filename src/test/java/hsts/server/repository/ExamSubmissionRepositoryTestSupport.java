package hsts.server.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;

final class ExamSubmissionRepositoryTestSupport {
    static final LocalDateTime OPENING = LocalDateTime.of(2026, 8, 1, 9, 0);
    static final LocalDateTime CLOSING = LocalDateTime.of(2026, 8, 1, 12, 0);
    static final LocalDateTime STARTED = LocalDateTime.of(2026, 8, 1, 9, 15);

    private ExamSubmissionRepositoryTestSupport() {
    }

    static Map<Object, Object> executionRow(String status) {
        return row(
                "execution_id", 81,
                "execution_code", "A1B2",
                "exam_id", 40,
                "exam_version_no", 3,
                "opening_time", OPENING,
                "closing_time", CLOSING,
                "duration_minutes", 75,
                "status", status,
                "exam_title", "Approved Midterm",
                "student_instructions", "Read each question carefully"
        );
    }

    static Map<Object, Object> executionSubmissionRow(String status,
                                                       LocalDateTime startedAt,
                                                       int allocatedMinutes,
                                                       int extraMinutes) {
        return row(
                "submission_id", 501,
                "started_at", startedAt,
                "status", status,
                "allocated_duration_minutes", allocatedMinutes,
                "extra_minutes", extraMinutes
        );
    }

    static Map<Object, Object> completeSubmissionRow(String status,
                                                      LocalDateTime startedAt,
                                                      int allocatedMinutes,
                                                      int extraMinutes) {
        Map<Object, Object> row = executionRow("OPEN");
        row.put("submission_id", 501);
        row.put("started_at", startedAt);
        row.put("status", status);
        row.put("allocated_duration_minutes", allocatedMinutes);
        row.put("extra_minutes", extraMinutes);
        row.put("execution_status", "OPEN");
        return row;
    }

    static Map<Object, Object> safeQuestionRow(int questionId, int versionNo,
                                                int orderNumber, String content,
                                                BigDecimal score) {
        return row(
                "question_id", questionId,
                "question_version_no", versionNo,
                "order_number", orderNumber,
                "score", score,
                "content", content,
                "topic", "Algebra",
                "difficulty", "HARD",
                "illustration_path", "diagram.png",
                "answer_option_1", "One",
                "answer_option_2", "Two",
                "answer_option_3", "Three",
                "answer_option_4", "Four"
        );
    }

    static Map<Object, Object> answerRow(int questionId, int selectedOption,
                                         LocalDateTime updatedAt) {
        return row(
                "question_id", questionId,
                "selected_option_number", selectedOption,
                "updated_at", updatedAt
        );
    }

    static Map<Object, Object> gradingRow(int questionId, int versionNo,
                                          BigDecimal score, int correctOption,
                                          Integer answerId, Integer selectedOption) {
        return row(
                "question_id", questionId,
                "question_version_no", versionNo,
                "score", score,
                "correct_option_number", correctOption,
                "answer_id", answerId,
                "selected_option_number", selectedOption
        );
    }

    static SafeAttemptPlans planSafeAttempt(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database
    ) {
        SafeAttemptPlans plans = new SafeAttemptPlans();
        plans.questions = database.plan("option_4.option_text AS answer_option_4")
                .queryRows(
                        safeQuestionRow(17, 4, 1, "Historical question",
                                new BigDecimal("40.00")),
                        safeQuestionRow(18, 2, 2, "Second historical question",
                                new BigDecimal("60.00"))
                );
        plans.answers = database.plan("ORDER BY answer.question_id ASC")
                .queryRows(answerRow(17, 2, STARTED.plusMinutes(5)));
        return plans;
    }

    static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }

    static final class SafeAttemptPlans {
        ExamRepositoryJdbcTestSupport.StatementPlan questions;
        ExamRepositoryJdbcTestSupport.StatementPlan answers;
    }
}
