package hsts.common;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AddBotQuestionSourcesPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final List<BotQuestionVersionReference> questions;

    public AddBotQuestionSourcesPayload(
            int botId, List<BotQuestionVersionReference> questions
    ) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        this.questions = BotContractSupport.immutableList(
                questions,
                "Question source references are required",
                "Question source references cannot contain null"
        );
        if (this.questions.isEmpty()) {
            throw new IllegalArgumentException("Question source references cannot be empty");
        }
        Set<String> identities = new HashSet<>();
        for (BotQuestionVersionReference question : this.questions) {
            String identity = question.getQuestionId() + ":" + question.getQuestionVersionNo();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Question source references cannot contain duplicates"
                );
            }
        }
    }

    public int getBotId() { return botId; }
    public List<BotQuestionVersionReference> getQuestions() { return questions; }
}
