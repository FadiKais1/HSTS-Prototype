package hsts.common;

import java.io.Serializable;

public class ExamVersionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int examId;
    private final int expectedVersionNo;

    public ExamVersionPayload(int examId, int expectedVersionNo) {
        this.examId = examId;
        this.expectedVersionNo = expectedVersionNo;
    }

    public int getExamId() { return examId; }
    public int getExpectedVersionNo() { return expectedVersionNo; }
}
