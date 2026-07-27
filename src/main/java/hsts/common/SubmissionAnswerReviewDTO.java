package hsts.common;

import java.io.Serializable;
import java.math.BigDecimal;

public class SubmissionAnswerReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final int questionVersionNo;
    private final int orderNumber;
    private final String questionContent;
    private final String selectedOptionText;
    private final Boolean correct;
    private final BigDecimal awardedScore;
    private final BigDecimal maximumScore;

    public SubmissionAnswerReviewDTO(
            int questionId, int questionVersionNo, int orderNumber,
            String questionContent, String selectedOptionText, Boolean correct,
            BigDecimal awardedScore, BigDecimal maximumScore
    ) {
        this.questionId = questionId;
        this.questionVersionNo = questionVersionNo;
        this.orderNumber = orderNumber;
        this.questionContent = questionContent;
        this.selectedOptionText = selectedOptionText;
        this.correct = correct;
        this.awardedScore = awardedScore;
        this.maximumScore = maximumScore;
    }

    public int getQuestionId() { return questionId; }
    public int getQuestionVersionNo() { return questionVersionNo; }
    public int getOrderNumber() { return orderNumber; }
    public String getQuestionContent() { return questionContent; }
    public String getSelectedOptionText() { return selectedOptionText; }
    public Boolean getCorrect() { return correct; }
    public BigDecimal getAwardedScore() { return awardedScore; }
    public BigDecimal getMaximumScore() { return maximumScore; }
}
