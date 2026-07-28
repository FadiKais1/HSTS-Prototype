package hsts.common;

import hsts.common.type.BotAnswerStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public final class BotMessageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int messageId;
    private final int sequenceNumber;
    private final String questionText;
    private final String answerText;
    private final BotAnswerStatus answerStatus;
    private final LocalDateTime createdAt;

    public BotMessageDTO(int messageId, int sequenceNumber, String questionText,
                         String answerText, BotAnswerStatus answerStatus,
                         LocalDateTime createdAt) {
        this.messageId = BotContractSupport.requirePositive(
                messageId, "Message ID must be positive"
        );
        this.sequenceNumber = BotContractSupport.requirePositive(
                sequenceNumber, "Message sequence number must be positive"
        );
        this.questionText = BotContractSupport.requireNonBlank(
                questionText, "Bot question is required"
        );
        if (answerStatus == null) {
            throw new IllegalArgumentException("Bot answer status is required");
        }
        this.answerStatus = answerStatus;
        this.answerText = normalizeAnswer(answerText, answerStatus);
        if (createdAt == null) {
            throw new IllegalArgumentException("Bot message time is required");
        }
        this.createdAt = createdAt;
    }

    public int getMessageId() { return messageId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getQuestionText() { return questionText; }
    public String getAnswerText() { return answerText; }
    public BotAnswerStatus getAnswerStatus() { return answerStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    private static String normalizeAnswer(String answerText,
                                          BotAnswerStatus answerStatus) {
        if (answerStatus == BotAnswerStatus.ANSWERED) {
            return BotContractSupport.requireNonBlank(
                    answerText, "Answered Bot message requires answer text"
            );
        }
        return answerText == null ? "" : answerText.trim();
    }
}
