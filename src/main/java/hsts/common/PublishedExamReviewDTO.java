package hsts.common;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PublishedExamReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final int executionId;
    private final String executionCode;
    private final int examId;
    private final int examVersionNo;
    private final String examTitle;
    private final int courseId;
    private final String courseName;
    private final BigDecimal finalScore;
    private final String teacherFeedback;
    private final LocalDateTime submittedAt;
    private final LocalDateTime reviewedAt;
    private final LocalDateTime publishedAt;
    private final List<PublishedExamQuestionReviewDTO> questions;

    public PublishedExamReviewDTO(
            int submissionId, int executionId, String executionCode,
            int examId, int examVersionNo, String examTitle,
            int courseId, String courseName, BigDecimal finalScore,
            String teacherFeedback, LocalDateTime submittedAt,
            LocalDateTime reviewedAt, LocalDateTime publishedAt,
            List<PublishedExamQuestionReviewDTO> questions
    ) {
        requirePositive(submissionId, "Submission ID must be positive");
        requirePositive(executionId, "Execution ID must be positive");
        requireText(executionCode, "Execution code is required");
        requirePositive(examId, "Exam ID must be positive");
        requirePositive(examVersionNo, "Exam version must be positive");
        requireText(examTitle, "Exam title is required");
        requirePositive(courseId, "Course ID must be positive");
        requireText(courseName, "Course name is required");
        BigDecimal safeFinalScore = Objects.requireNonNull(
                finalScore, "Final score is required"
        );
        if (safeFinalScore.compareTo(BigDecimal.ZERO) < 0
                || safeFinalScore.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException(
                    "Final score must be between zero and 100"
            );
        }
        Objects.requireNonNull(submittedAt, "Submitted timestamp is required");
        Objects.requireNonNull(publishedAt, "Published timestamp is required");
        List<PublishedExamQuestionReviewDTO> safeQuestions = List.copyOf(
                Objects.requireNonNull(questions, "Question reviews are required")
        );
        validateQuestions(safeQuestions);

        this.submissionId = submissionId;
        this.executionId = executionId;
        this.executionCode = executionCode;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.examTitle = examTitle;
        this.courseId = courseId;
        this.courseName = courseName;
        this.finalScore = safeFinalScore;
        this.teacherFeedback = teacherFeedback;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.publishedAt = publishedAt;
        this.questions = safeQuestions;
    }

    public int getSubmissionId() { return submissionId; }
    public int getExecutionId() { return executionId; }
    public String getExecutionCode() { return executionCode; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamTitle() { return examTitle; }
    public int getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public BigDecimal getFinalScore() { return finalScore; }
    public String getTeacherFeedback() { return teacherFeedback; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public List<PublishedExamQuestionReviewDTO> getQuestions() { return questions; }

    private static void validateQuestions(
            List<PublishedExamQuestionReviewDTO> questions
    ) {
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Published exam questions are required");
        }
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < questions.size(); index++) {
            PublishedExamQuestionReviewDTO question = questions.get(index);
            if (question.getOrderNumber() != index + 1) {
                throw new IllegalArgumentException(
                        "Published exam question order must be contiguous"
                );
            }
            String identity = question.getQuestionId() + ":"
                    + question.getQuestionVersionNo();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Published exam question identity is duplicated"
                );
            }
        }
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) throw new IllegalArgumentException(message);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}
