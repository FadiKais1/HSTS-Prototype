package hsts.common;

import java.io.Serializable;

public final class QuestionVersionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final int versionNo;

    public QuestionVersionPayload(int questionId, int versionNo) {
        this.questionId = questionId;
        this.versionNo = versionNo;
    }

    public int getQuestionId() { return questionId; }
    public int getVersionNo() { return versionNo; }
}
