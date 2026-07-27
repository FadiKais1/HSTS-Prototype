package hsts.common;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReviewSubmissionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final BigDecimal finalScore;
    private final String feedback;
    private final String adjustmentReason;
    private final LocalDateTime expectedUpdatedAt;

    public ReviewSubmissionPayload(
            int submissionId, BigDecimal finalScore, String feedback,
            String adjustmentReason, LocalDateTime expectedUpdatedAt
    ) {
        this.submissionId = submissionId;
        this.finalScore = finalScore;
        this.feedback = feedback;
        this.adjustmentReason = adjustmentReason;
        this.expectedUpdatedAt = expectedUpdatedAt;
    }

    public int getSubmissionId() { return submissionId; }
    public BigDecimal getFinalScore() { return finalScore; }
    public String getFeedback() { return feedback; }
    public String getAdjustmentReason() { return adjustmentReason; }
    public LocalDateTime getExpectedUpdatedAt() { return expectedUpdatedAt; }
}
