package hsts.common;

import hsts.common.type.PublishedAnswerOutcome;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class PublishedExamQuestionReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int orderNumber;
    private final int questionId;
    private final int questionVersionNo;
    private final String content;
    private final String topic;
    private final String difficulty;
    private final String type;
    private final String illustrationPath;
    private final List<String> answerOptions;
    private final Integer selectedOptionNumber;
    private final int correctOptionNumber;
    private final PublishedAnswerOutcome outcome;
    private final BigDecimal awardedScore;
    private final BigDecimal maximumScore;
    private final QuestionIllustrationDTO illustration;

    public PublishedExamQuestionReviewDTO(
            int orderNumber, int questionId, int questionVersionNo,
            String content, String topic, String difficulty, String type,
            String illustrationPath, List<String> answerOptions,
            Integer selectedOptionNumber, int correctOptionNumber,
            PublishedAnswerOutcome outcome, BigDecimal awardedScore,
            BigDecimal maximumScore
    ) {
        this(orderNumber, questionId, questionVersionNo, content, topic,
                difficulty, type, illustrationPath, answerOptions,
                selectedOptionNumber, correctOptionNumber, outcome,
                awardedScore, maximumScore, null);
    }

    public PublishedExamQuestionReviewDTO(
            int orderNumber, int questionId, int questionVersionNo,
            String content, String topic, String difficulty, String type,
            String illustrationPath, List<String> answerOptions,
            Integer selectedOptionNumber, int correctOptionNumber,
            PublishedAnswerOutcome outcome, BigDecimal awardedScore,
            BigDecimal maximumScore, QuestionIllustrationDTO illustration
    ) {
        requirePositive(orderNumber, "Question order must be positive");
        requirePositive(questionId, "Question ID must be positive");
        requirePositive(questionVersionNo, "Question version must be positive");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Question content is required");
        }
        List<String> options = List.copyOf(
                Objects.requireNonNull(answerOptions, "Answer options are required")
        );
        if (options.size() != 4 || options.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "Exactly four nonblank answer options are required"
            );
        }
        requireOption(correctOptionNumber, "Correct option must be between 1 and 4");
        if (selectedOptionNumber != null) {
            requireOption(selectedOptionNumber,
                    "Selected option must be between 1 and 4");
        }
        PublishedAnswerOutcome safeOutcome = Objects.requireNonNull(
                outcome, "Answer outcome is required"
        );
        validateOutcome(safeOutcome, selectedOptionNumber, correctOptionNumber);
        BigDecimal safeAwarded = Objects.requireNonNull(
                awardedScore, "Awarded score is required"
        );
        BigDecimal safeMaximum = Objects.requireNonNull(
                maximumScore, "Maximum score is required"
        );
        if (safeMaximum.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Maximum score must be positive");
        }
        if (safeAwarded.compareTo(BigDecimal.ZERO) < 0
                || safeAwarded.compareTo(safeMaximum) > 0) {
            throw new IllegalArgumentException(
                    "Awarded score must be between zero and maximum score"
            );
        }

        this.orderNumber = orderNumber;
        this.questionId = questionId;
        this.questionVersionNo = questionVersionNo;
        this.content = content;
        this.topic = topic;
        this.difficulty = difficulty;
        this.type = type;
        this.illustrationPath = illustrationPath;
        this.answerOptions = options;
        this.selectedOptionNumber = selectedOptionNumber;
        this.correctOptionNumber = correctOptionNumber;
        this.outcome = safeOutcome;
        this.awardedScore = safeAwarded;
        this.maximumScore = safeMaximum;
        this.illustration = illustration;
    }

    public int getOrderNumber() { return orderNumber; }
    public int getQuestionId() { return questionId; }
    public int getQuestionVersionNo() { return questionVersionNo; }
    public String getContent() { return content; }
    public String getTopic() { return topic; }
    public String getDifficulty() { return difficulty; }
    public String getType() { return type; }
    public String getIllustrationPath() { return illustrationPath; }
    public List<String> getAnswerOptions() { return answerOptions; }
    public Integer getSelectedOptionNumber() { return selectedOptionNumber; }
    public int getCorrectOptionNumber() { return correctOptionNumber; }
    public PublishedAnswerOutcome getOutcome() { return outcome; }
    public BigDecimal getAwardedScore() { return awardedScore; }
    public BigDecimal getMaximumScore() { return maximumScore; }
    public QuestionIllustrationDTO getIllustration() { return illustration; }

    private static void validateOutcome(PublishedAnswerOutcome outcome,
                                        Integer selected, int correct) {
        switch (outcome) {
            case UNANSWERED -> {
                if (selected != null) {
                    throw new IllegalArgumentException(
                            "Unanswered outcome cannot have a selected option"
                    );
                }
            }
            case CORRECT -> {
                if (selected == null || selected != correct) {
                    throw new IllegalArgumentException(
                            "Correct outcome requires the correct selected option"
                    );
                }
            }
            case INCORRECT -> {
                if (selected == null || selected == correct) {
                    throw new IllegalArgumentException(
                            "Incorrect outcome requires a different selected option"
                    );
                }
            }
        }
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) throw new IllegalArgumentException(message);
    }

    private static void requireOption(int value, String message) {
        if (value < 1 || value > 4) throw new IllegalArgumentException(message);
    }
}
