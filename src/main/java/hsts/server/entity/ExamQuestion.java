package hsts.server.entity;

public class ExamQuestion {
    private int examQuestionId;
    private int orderNumber;
    private double score;

    // RELATIONSHIP-DERIVED: Each exam question refers to one question.
    private Question question;

    public void updateScore(double score) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateOrder(int orderNumber) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
