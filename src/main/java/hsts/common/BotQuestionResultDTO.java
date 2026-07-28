package hsts.common;

import hsts.common.type.BotAnswerStatus;

import java.io.Serializable;

public final class BotQuestionResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final BotMessageDTO message;
    private final boolean suitableAnswer;

    public BotQuestionResultDTO(BotMessageDTO message, boolean suitableAnswer) {
        if (message == null) {
            throw new IllegalArgumentException("Bot message result is required");
        }
        boolean expected = message.getAnswerStatus() == BotAnswerStatus.ANSWERED;
        if (suitableAnswer != expected) {
            throw new IllegalArgumentException(
                    "Suitable-answer flag must agree with Bot answer status"
            );
        }
        this.message = message;
        this.suitableAnswer = suitableAnswer;
    }

    public BotMessageDTO getMessage() { return message; }
    public boolean isSuitableAnswer() { return suitableAnswer; }
}
