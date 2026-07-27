package hsts.common;

import hsts.common.type.SubmissionStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SubmissionReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final int executionId;
    private final int examId;
    private final int examVersionNo;
    private final String examTitle;
    private final int studentUserId;
    private final String studentName;
    private final SubmissionStatus status;
    private final BigDecimal automaticScore;
    private final BigDecimal finalScore;
    private final String teacherFeedback;
    private final String adjustmentReason;
    private final int reviewerUserId;
    private final LocalDateTime startedAt;
    private final LocalDateTime submittedAt;
    private final LocalDateTime reviewedAt;
    private final int publisherUserId;
    private final LocalDateTime publishedAt;
    private final List<SubmissionAnswerReviewDTO> answers;

    public SubmissionReviewDTO(
            int submissionId, int executionId, int examId, int examVersionNo,
            String examTitle, int studentUserId, String studentName,
            SubmissionStatus status, BigDecimal automaticScore,
            BigDecimal finalScore, String teacherFeedback,
            String adjustmentReason, int reviewerUserId,
            LocalDateTime startedAt, LocalDateTime submittedAt,
            LocalDateTime reviewedAt, int publisherUserId,
            LocalDateTime publishedAt, List<SubmissionAnswerReviewDTO> answers
    ) {
        this.submissionId = submissionId;
        this.executionId = executionId;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.examTitle = examTitle;
        this.studentUserId = studentUserId;
        this.studentName = studentName;
        this.status = status;
        this.automaticScore = automaticScore;
        this.finalScore = finalScore;
        this.teacherFeedback = teacherFeedback;
        this.adjustmentReason = adjustmentReason;
        this.reviewerUserId = reviewerUserId;
        this.startedAt = startedAt;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.publisherUserId = publisherUserId;
        this.publishedAt = publishedAt;
        this.answers = List.copyOf(answers);
    }

    public int getSubmissionId() { return submissionId; }
    public int getExecutionId() { return executionId; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamTitle() { return examTitle; }
    public int getStudentUserId() { return studentUserId; }
    public String getStudentName() { return studentName; }
    public SubmissionStatus getStatus() { return status; }
    public BigDecimal getAutomaticScore() { return automaticScore; }
    public BigDecimal getFinalScore() { return finalScore; }
    public String getTeacherFeedback() { return teacherFeedback; }
    public String getAdjustmentReason() { return adjustmentReason; }
    public int getReviewerUserId() { return reviewerUserId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public int getPublisherUserId() { return publisherUserId; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public List<SubmissionAnswerReviewDTO> getAnswers() { return answers; }
}
