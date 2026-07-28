package hsts.external;

import hsts.common.type.BotAnswerStatus;

public final class BotProviderTurn {
    private final String questionText;
    private final String answerText;
    private final BotAnswerStatus answerStatus;

    public BotProviderTurn(String questionText, String answerText,
                           BotAnswerStatus answerStatus) {
        this.questionText = requireText(questionText, "Bot question is required");
        if (answerStatus == null) {
            throw new IllegalArgumentException("Bot answer status is required");
        }
        this.answerStatus = answerStatus;
        this.answerText = answerStatus == BotAnswerStatus.ANSWERED
                ? requireText(answerText, "Answered Bot turn requires answer text")
                : "";
    }

    public String getQuestionText() { return questionText; }
    public String getAnswerText() { return answerText; }
    public BotAnswerStatus getAnswerStatus() { return answerStatus; }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > 10_000) {
            throw new IllegalArgumentException("Bot provider turn text is too long");
        }
        return normalized;
    }
}
