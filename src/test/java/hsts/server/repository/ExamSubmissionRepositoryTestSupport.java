package hsts.server.repository;

import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;
import hsts.server.entity.ExamExecution;
import hsts.server.entity.ExamSubmission;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    static Map<Object, Object> entityLockRow(String status,
                                             LocalDateTime startedAt,
                                             int allocatedMinutes,
                                             int extraMinutes) {
        Map<Object, Object> row = completeSubmissionRow(
                status, startedAt, allocatedMinutes, extraMinutes
        );
        row.put("student_user_id", 1001);
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

    static ExamExecution executionEntity() {
        return ExamExecution.rehydrate(
                81, "A1B2", 40, 3, OPENING, CLOSING, 75,
                ExecutionStatus.OPEN, 1002, OPENING.minusDays(1), null,
                null, null, List.of(), 0, 0, 0, OPENING, List.of()
        );
    }

    static ExamSubmission inProgressSubmission(LocalDateTime startedAt,
                                                int extraMinutes) {
        return ExamSubmission.rehydrate(
                501, 81, 40, 3, 1001, startedAt, null,
                SubmissionStatus.IN_PROGRESS, 75, extraMinutes,
                extraMinutes == 0 ? null : "Accommodation",
                null, null, null, null, null, null, null, null, null,
                startedAt, startedAt, List.of()
        );
    }

    @SafeVarargs
    static void planInternalEntity(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database,
            String status,
            LocalDateTime startedAt,
            int extraMinutes,
            Map<Object, Object>... answerRows
    ) {
        database.plan("submission.updated_at").queryRows(submissionEntityRow(
                501,
                status,
                startedAt,
                extraMinutes
        ));
        database.plan("ORDER BY selection.order_number ASC,")
                .queryRows(answerRows);
    }

    static Map<Object, Object> submissionEntityRow(
            int submissionId,
            String status,
            LocalDateTime startedAt,
            int extraMinutes
    ) {
        return row(
                "submission_id", submissionId,
                "execution_id", 81,
                "exam_id", 40,
                "exam_version_no", 3,
                "student_user_id", 1001,
                "started_at", startedAt,
                "submitted_at", null,
                "status", status,
                "allocated_duration_minutes", 75,
                "extra_minutes", extraMinutes,
                "extension_reason", extraMinutes == 0 ? null : "Accommodation",
                "actual_duration_minutes", null,
                "automatic_score", null,
                "final_score", null,
                "teacher_feedback", null,
                "manual_change_reason", null,
                "reviewed_by_user_id", null,
                "reviewed_at", null,
                "published_by_user_id", null,
                "published_at", null,
                "created_at", startedAt,
                "updated_at", startedAt
        );
    }

    static Map<Object, Object> entityAnswerRow(
            int answerId,
            int questionId,
            int versionNo,
            int orderNumber,
            int selectedOption,
            LocalDateTime updatedAt
    ) {
        return row(
                "answer_id", answerId,
                "submission_id", 501,
                "question_id", questionId,
                "question_version_no", versionNo,
                "selected_option_number", selectedOption,
                "answer_content", null,
                "is_correct", null,
                "score_received", null,
                "created_at", startedAtForAnswer(updatedAt),
                "updated_at", updatedAt,
                "order_number", orderNumber
        );
    }

    private static LocalDateTime startedAtForAnswer(LocalDateTime updatedAt) {
        return updatedAt.minusMinutes(1);
    }

    static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }

    static final class SafeAttemptPlans {
        ExamRepositoryJdbcTestSupport.StatementPlan questions;
        ExamRepositoryJdbcTestSupport.StatementPlan answers;
    }
}
