package hsts.common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class PublishSubmissionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final LocalDateTime expectedUpdatedAt;

    public PublishSubmissionPayload(int submissionId,
                                    LocalDateTime expectedUpdatedAt) {
        this.submissionId = submissionId;
        this.expectedUpdatedAt = expectedUpdatedAt;
    }

    public int getSubmissionId() { return submissionId; }
    public LocalDateTime getExpectedUpdatedAt() { return expectedUpdatedAt; }
}
