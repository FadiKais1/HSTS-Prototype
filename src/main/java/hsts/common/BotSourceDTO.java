package hsts.common;

import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;

import java.io.Serializable;
import java.time.LocalDateTime;

public final class BotSourceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int sourceId;
    private final int botId;
    private final BotSourceType sourceType;
    private final String displayName;
    private final Integer questionId;
    private final Integer questionVersionNo;
    private final BotSourceStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime removedAt;

    /**
     * Which revision of this source is in use. Sent back when a teacher saves an
     * edit, so a colleague's change is detected.
     */
    private int currentVersionNo = 1;

    public BotSourceDTO(int sourceId, int botId, BotSourceType sourceType,
                        String displayName, Integer questionId,
                        Integer questionVersionNo, BotSourceStatus status,
                        LocalDateTime createdAt, LocalDateTime removedAt) {
        this.sourceId = BotContractSupport.requirePositive(
                sourceId, "Source ID must be positive"
        );
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        if (sourceType == null) {
            throw new IllegalArgumentException("Bot source type is required");
        }
        this.sourceType = sourceType;
        this.displayName = BotContractSupport.requireNonBlank(
                displayName, "Source display name is required"
        );
        validateQuestionReference(sourceType, questionId, questionVersionNo);
        this.questionId = questionId;
        this.questionVersionNo = questionVersionNo;
        if (status == null) {
            throw new IllegalArgumentException("Bot source status is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Source creation time is required");
        }
        if (status == BotSourceStatus.ACTIVE && removedAt != null) {
            throw new IllegalArgumentException("Active source cannot have a removal time");
        }
        if (status == BotSourceStatus.REMOVED && removedAt == null) {
            throw new IllegalArgumentException("Removed source requires a removal time");
        }
        if (removedAt != null && removedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Source removal time cannot precede creation");
        }
        this.status = status;
        this.createdAt = createdAt;
        this.removedAt = removedAt;
    }

    public int getCurrentVersionNo() { return currentVersionNo; }

    public void setCurrentVersionNo(int currentVersionNo) {
        this.currentVersionNo = currentVersionNo <= 0 ? 1 : currentVersionNo;
    }

    public int getSourceId() { return sourceId; }
    public int getBotId() { return botId; }
    public BotSourceType getSourceType() { return sourceType; }
    public String getDisplayName() { return displayName; }
    public Integer getQuestionId() { return questionId; }
    public Integer getQuestionVersionNo() { return questionVersionNo; }
    public BotSourceStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRemovedAt() { return removedAt; }

    private static void validateQuestionReference(BotSourceType sourceType,
                                                  Integer questionId,
                                                  Integer questionVersionNo) {
        if (sourceType == BotSourceType.QUESTION_BANK) {
            if (questionId == null || questionId <= 0
                    || questionVersionNo == null || questionVersionNo <= 0) {
                throw new IllegalArgumentException(
                        "Question-bank source requires a positive question and version"
                );
            }
        } else if (questionId != null || questionVersionNo != null) {
            throw new IllegalArgumentException(
                    "Non-question source cannot reference a question version"
            );
        }
    }
}
