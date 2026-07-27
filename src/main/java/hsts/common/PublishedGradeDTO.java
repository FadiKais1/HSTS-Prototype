package hsts.common;

import hsts.common.type.SubmissionStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PublishedGradeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final int executionId;
    private final int examId;
    private final int examVersionNo;
    private final String examTitle;
    private final String courseName;
    private final SubmissionStatus status;
    private final BigDecimal finalScore;
    private final String teacherFeedback;
    private final LocalDateTime submittedAt;
    private final LocalDateTime reviewedAt;
    private final LocalDateTime publishedAt;

    public PublishedGradeDTO(
            int submissionId, int executionId, int examId, int examVersionNo,
            String examTitle, String courseName, SubmissionStatus status,
            BigDecimal finalScore, String teacherFeedback,
            LocalDateTime submittedAt, LocalDateTime reviewedAt,
            LocalDateTime publishedAt
    ) {
        this.submissionId = submissionId;
        this.executionId = executionId;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.examTitle = examTitle;
        this.courseName = courseName;
        this.status = status;
        this.finalScore = finalScore;
        this.teacherFeedback = teacherFeedback;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.publishedAt = publishedAt;
    }

    public int getSubmissionId() { return submissionId; }
    public int getExecutionId() { return executionId; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamTitle() { return examTitle; }
    public String getCourseName() { return courseName; }
    public SubmissionStatus getStatus() { return status; }
    public BigDecimal getFinalScore() { return finalScore; }
    public String getTeacherFeedback() { return teacherFeedback; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}
