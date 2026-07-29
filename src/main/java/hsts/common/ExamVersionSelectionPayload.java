package hsts.common;

import java.io.Serializable;

public final class ExamVersionSelectionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int examId;
    private final int versionNo;

    public ExamVersionSelectionPayload(int examId, int versionNo) {
        this.examId = examId;
        this.versionNo = versionNo;
    }

    public int getExamId() { return examId; }
    public int getVersionNo() { return versionNo; }
}
