package hsts.common;

import java.io.Serializable;

public final class CommonBotQuestionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String questionText;
    private final int occurrenceCount;

    public CommonBotQuestionDTO(String questionText, int occurrenceCount) {
        this.questionText = BotContractSupport.requireNonBlank(
                questionText, "Common Bot question is required"
        );
        this.occurrenceCount = BotContractSupport.requirePositive(
                occurrenceCount, "Question occurrence count must be positive"
        );
    }

    public String getQuestionText() { return questionText; }
    public int getOccurrenceCount() { return occurrenceCount; }
}
