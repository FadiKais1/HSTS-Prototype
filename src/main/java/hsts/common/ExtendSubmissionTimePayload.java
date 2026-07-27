package hsts.common;

import java.io.Serializable;

public class ExtendSubmissionTimePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final int extraMinutes;
    private final String reason;

    public ExtendSubmissionTimePayload(int submissionId, int extraMinutes,
                                       String reason) {
        this.submissionId = submissionId;
        this.extraMinutes = extraMinutes;
        this.reason = reason;
    }

    public int getSubmissionId() { return submissionId; }
    public int getExtraMinutes() { return extraMinutes; }
    public String getReason() { return reason; }
}
