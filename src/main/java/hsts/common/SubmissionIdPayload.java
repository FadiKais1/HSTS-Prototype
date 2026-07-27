package hsts.common;

import java.io.Serializable;

public class SubmissionIdPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;

    public SubmissionIdPayload(int submissionId) {
        this.submissionId = submissionId;
    }

    public int getSubmissionId() { return submissionId; }
}
