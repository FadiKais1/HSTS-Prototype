package hsts.common;

import hsts.common.type.SubmissionStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExecutionSubmissionSummaryDTO implements Serializable {
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
    private final LocalDateTime startedAt;
    private final LocalDateTime submittedAt;
    private final LocalDateTime reviewedAt;
    private final LocalDateTime publishedAt;

    public ExecutionSubmissionSummaryDTO(
            int submissionId, int executionId, int examId, int examVersionNo,
            String examTitle, int studentUserId, String studentName,
            SubmissionStatus status, BigDecimal automaticScore,
            BigDecimal finalScore, LocalDateTime startedAt,
            LocalDateTime submittedAt, LocalDateTime reviewedAt,
            LocalDateTime publishedAt
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
        this.startedAt = startedAt;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.publishedAt = publishedAt;
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
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}
