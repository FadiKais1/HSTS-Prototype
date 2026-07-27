package hsts.common;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PublishedGradeSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final int executionId;
    private final int examId;
    private final int examVersionNo;
    private final String examTitle;
    private final String courseName;
    private final BigDecimal finalScore;
    private final LocalDateTime submittedAt;
    private final LocalDateTime publishedAt;

    public PublishedGradeSummaryDTO(
            int submissionId, int executionId, int examId, int examVersionNo,
            String examTitle, String courseName, BigDecimal finalScore,
            LocalDateTime submittedAt, LocalDateTime publishedAt
    ) {
        this.submissionId = submissionId;
        this.executionId = executionId;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.examTitle = examTitle;
        this.courseName = courseName;
        this.finalScore = finalScore;
        this.submittedAt = submittedAt;
        this.publishedAt = publishedAt;
    }

    public int getSubmissionId() { return submissionId; }
    public int getExecutionId() { return executionId; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamTitle() { return examTitle; }
    public String getCourseName() { return courseName; }
    public BigDecimal getFinalScore() { return finalScore; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}
