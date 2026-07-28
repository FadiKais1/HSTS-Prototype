package hsts.external;

import hsts.common.type.BotAnswerStatus;

public final class BotProviderResponse {
    private final BotAnswerStatus answerStatus;
    private final String answerText;
    private final String providerRequestId;

    public BotProviderResponse(BotAnswerStatus answerStatus, String answerText,
                               String providerRequestId) {
        if (answerStatus == null) {
            throw new IllegalArgumentException("Bot answer status is required");
        }
        this.answerStatus = answerStatus;
        this.answerText = answerStatus == BotAnswerStatus.ANSWERED
                ? requireAnswer(answerText) : "";
        this.providerRequestId = normalizeNullable(providerRequestId);
    }

    public BotAnswerStatus getAnswerStatus() { return answerStatus; }
    public String getAnswerText() { return answerText; }
    public String getProviderRequestId() { return providerRequestId; }

    private static String requireAnswer(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Answered provider response requires answer text");
        }
        String normalized = value.trim();
        if (normalized.length() > 4_000) {
            throw new IllegalArgumentException("Provider answer exceeds 4000 characters");
        }
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
