package hsts.common;

import java.io.Serializable;

public class UpdateQuestionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final String content;

    public UpdateQuestionPayload(int questionId, String content) {
        this.questionId = questionId;
        this.content = content;
    }

    public int getQuestionId() {
        return questionId;
    }

    public String getContent() {
        return content;
    }
}
