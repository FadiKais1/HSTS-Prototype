package hsts.common;

import java.io.Serializable;

public class ExamQuestionSelectionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final int questionVersionNo;
    private final int orderNumber;
    private final double score;

    public ExamQuestionSelectionPayload(int questionId, int questionVersionNo,
                                        int orderNumber, double score) {
        this.questionId = questionId;
        this.questionVersionNo = questionVersionNo;
        this.orderNumber = orderNumber;
        this.score = score;
    }

    public int getQuestionId() { return questionId; }
    public int getQuestionVersionNo() { return questionVersionNo; }
    public int getOrderNumber() { return orderNumber; }
    public double getScore() { return score; }
}
