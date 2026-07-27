package hsts.common;

import java.io.Serializable;

public class RejectExamPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int examId;
    private final int expectedVersionNo;
    private final String reason;

    public RejectExamPayload(int examId, int expectedVersionNo, String reason) {
        this.examId = examId;
        this.expectedVersionNo = expectedVersionNo;
        this.reason = reason;
    }

    public int getExamId() { return examId; }
    public int getExpectedVersionNo() { return expectedVersionNo; }
    public String getReason() { return reason; }
}
