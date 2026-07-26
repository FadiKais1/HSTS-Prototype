package hsts.server.entity;

public class StudentAnswer {
    private int answerId;
    private String answerContent;
    private int selectedOptionId;
    private boolean isCorrect;
    private double scoreReceived;

    public void updateAnswer(int answerChoice) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean checkCorrectness() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void assignScore(double score) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
