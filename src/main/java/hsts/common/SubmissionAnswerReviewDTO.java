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
    private final QuestionIllustrationDTO illustration;

    public SubmissionAnswerReviewDTO(
            int questionId, int questionVersionNo, int orderNumber,
            String questionContent, String selectedOptionText, Boolean correct,
            BigDecimal awardedScore, BigDecimal maximumScore
    ) {
        this(questionId, questionVersionNo, orderNumber, questionContent,
                selectedOptionText, correct, awardedScore, maximumScore, null);
    }

    public SubmissionAnswerReviewDTO(
            int questionId, int questionVersionNo, int orderNumber,
            String questionContent, String selectedOptionText, Boolean correct,
            BigDecimal awardedScore, BigDecimal maximumScore,
            QuestionIllustrationDTO illustration
    ) {
        this.questionId = questionId;
        this.questionVersionNo = questionVersionNo;
        this.orderNumber = orderNumber;
        this.questionContent = questionContent;
        this.selectedOptionText = selectedOptionText;
        this.correct = correct;
        this.awardedScore = awardedScore;
        this.maximumScore = maximumScore;
        this.illustration = illustration;
    }

    public int getQuestionId() { return questionId; }
    public int getQuestionVersionNo() { return questionVersionNo; }
    public int getOrderNumber() { return orderNumber; }
    public String getQuestionContent() { return questionContent; }
    public String getSelectedOptionText() { return selectedOptionText; }
    public Boolean getCorrect() { return correct; }
    public BigDecimal getAwardedScore() { return awardedScore; }
    public BigDecimal getMaximumScore() { return maximumScore; }
    public QuestionIllustrationDTO getIllustration() { return illustration; }
}
