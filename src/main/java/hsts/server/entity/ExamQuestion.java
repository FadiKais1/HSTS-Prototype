package hsts.server.entity;

import java.math.BigDecimal;

public class ExamQuestion {
    private final int examQuestionId;
    private final int questionVersionNo;
    private int orderNumber;
    private BigDecimal score;

    // RELATIONSHIP-DERIVED: Each exam question refers to one immutable question snapshot.
    private final Question question;

    public ExamQuestion(int examQuestionId, int questionVersionNo,
                        int orderNumber, BigDecimal score, Question question) {
        if (examQuestionId <= 0) {
            throw new IllegalArgumentException("Question ID must be positive");
        }
        if (questionVersionNo <= 0) {
            throw new IllegalArgumentException("Question version must be positive");
        }
        if (question == null) {
            throw new IllegalArgumentException("Question snapshot is required");
        }
        if (question.getQuestionId() != examQuestionId) {
            throw new IllegalArgumentException("Question snapshot identity does not match selection");
        }
        requirePositiveOrder(orderNumber);
        this.examQuestionId = examQuestionId;
        this.questionVersionNo = questionVersionNo;
        this.orderNumber = orderNumber;
        this.score = requirePositiveScore(score);
        this.question = copyQuestion(question);
    }

    public static ExamQuestion select(int questionVersionNo, int orderNumber,
                                      BigDecimal score, Question question) {
        if (question == null) {
            throw new IllegalArgumentException("Question snapshot is required");
        }
        return new ExamQuestion(
                question.getQuestionId(),
                questionVersionNo,
                orderNumber,
                score,
                question
        );
    }

    public int getExamQuestionId() {
        return examQuestionId;
    }

    public int getQuestionId() {
        return examQuestionId;
    }

    public int getQuestionVersionNo() {
        return questionVersionNo;
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    // COMPATIBILITY-ONLY: The diagram exposes score as double.
    public double getScore() {
        return score.doubleValue();
    }

    public BigDecimal getScoreValue() {
        return score;
    }

    public Question getQuestion() {
        return copyQuestion(question);
    }

    public void updateScore(double score) {
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("Question score must be positive and finite");
        }
        updateScore(BigDecimal.valueOf(score));
    }

    public void updateScore(BigDecimal score) {
        this.score = requirePositiveScore(score);
    }

    public void updateOrder(int orderNumber) {
        requirePositiveOrder(orderNumber);
        this.orderNumber = orderNumber;
    }

    ExamQuestion copy() {
        return new ExamQuestion(
                examQuestionId,
                questionVersionNo,
                orderNumber,
                score,
                question
        );
    }

    private static Question copyQuestion(Question source) {
        return Question.rehydrate(
                source.getQuestionId(),
                source.getContent(),
                source.getQuestionType(),
                source.getDifficultyLevel(),
                source.getQuestionStatus(),
                source.getCreatedAt(),
                source.getUpdatedAt(),
                source.getTopic(),
                source.getIllustrationPath(),
                source.getAnswerOptions()
        );
    }

    private static void requirePositiveOrder(int orderNumber) {
        if (orderNumber <= 0) {
            throw new IllegalArgumentException("Question order must be positive");
        }
    }

    private static BigDecimal requirePositiveScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Question score must be positive");
        }
        return score;
    }
}
