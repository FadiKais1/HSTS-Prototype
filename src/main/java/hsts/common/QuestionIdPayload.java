package hsts.common;

import java.io.Serializable;

public class QuestionIdPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;

    public QuestionIdPayload(int questionId) {
        this.questionId = questionId;
    }

    public int getQuestionId() { return questionId; }
}
