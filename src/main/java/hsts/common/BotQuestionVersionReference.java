package hsts.common;

import java.io.Serializable;

public final class BotQuestionVersionReference implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final int questionVersionNo;

    public BotQuestionVersionReference(int questionId, int questionVersionNo) {
        this.questionId = BotContractSupport.requirePositive(
                questionId, "Question ID must be positive"
        );
        this.questionVersionNo = BotContractSupport.requirePositive(
                questionVersionNo, "Question version must be positive"
        );
    }

    public int getQuestionId() { return questionId; }
    public int getQuestionVersionNo() { return questionVersionNo; }
}
