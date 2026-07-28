package hsts.server.entity;

import hsts.common.type.BotAnswerStatus;

import java.time.LocalDateTime;

public final class BotMessage {
    private final int messageId;
    private final int conversationId;
    private final int sequenceNumber;
    private final String questionText;
    private final String normalizedQuestion;
    private final String answerText;
    private final BotAnswerStatus answerStatus;
    private final String providerRequestId;
    private final LocalDateTime createdAt;

    private BotMessage(int messageId, int conversationId, int sequenceNumber,
                       String questionText, String normalizedQuestion,
                       String answerText, BotAnswerStatus answerStatus,
                       String providerRequestId, LocalDateTime createdAt) {
        if (messageId < 0) {
            throw new IllegalArgumentException("Bot message ID cannot be negative");
        }
        if (conversationId <= 0) {
            throw new IllegalArgumentException("Bot conversation ID must be positive");
        }
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("Bot message sequence must be positive");
        }
        this.questionText = requireNonBlank(questionText, "Bot question is required");
        this.normalizedQuestion = requireNonBlank(
                normalizedQuestion, "Normalized Bot question is required"
        );
        if (answerStatus == null) {
            throw new IllegalArgumentException("Bot answer status is required");
        }
        this.answerText = normalizeAnswer(answerText, answerStatus);
        if (createdAt == null) {
            throw new IllegalArgumentException("Bot message creation time is required");
        }
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.sequenceNumber = sequenceNumber;
        this.answerStatus = answerStatus;
        this.providerRequestId = normalizeNullable(providerRequestId);
        this.createdAt = createdAt;
    }

    public static BotMessage create(int conversationId, int sequenceNumber,
                                    String questionText, String normalizedQuestion,
                                    String answerText, BotAnswerStatus answerStatus,
                                    String providerRequestId,
                                    LocalDateTime createdAt) {
        return new BotMessage(
                0, conversationId, sequenceNumber, questionText,
                normalizedQuestion, answerText, answerStatus,
                providerRequestId, createdAt
        );
    }

    public static BotMessage rehydrate(int messageId, int conversationId,
                                       int sequenceNumber, String questionText,
                                       String normalizedQuestion, String answerText,
                                       BotAnswerStatus answerStatus,
                                       String providerRequestId,
                                       LocalDateTime createdAt) {
        if (messageId <= 0) {
            throw new IllegalArgumentException("Persisted Bot message ID must be positive");
        }
        return new BotMessage(
                messageId, conversationId, sequenceNumber, questionText,
                normalizedQuestion, answerText, answerStatus,
                providerRequestId, createdAt
        );
    }

    public int getMessageId() { return messageId; }
    public int getConversationId() { return conversationId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getQuestionText() { return questionText; }
    public String getNormalizedQuestion() { return normalizedQuestion; }
    public String getAnswerText() { return answerText; }
    public BotAnswerStatus getAnswerStatus() { return answerStatus; }
    public String getProviderRequestId() { return providerRequestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    BotMessage copy() {
        return new BotMessage(
                messageId, conversationId, sequenceNumber, questionText,
                normalizedQuestion, answerText, answerStatus,
                providerRequestId, createdAt
        );
    }

    private static String normalizeAnswer(String answer,
                                          BotAnswerStatus answerStatus) {
        if (answerStatus == BotAnswerStatus.ANSWERED) {
            return requireNonBlank(answer, "Answered Bot message requires answer text");
        }
        return answer == null ? "" : answer.trim();
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
