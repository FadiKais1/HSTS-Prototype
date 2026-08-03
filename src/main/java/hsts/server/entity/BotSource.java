package hsts.server.entity;

import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

public final class BotSource {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final int sourceId;
    private final int botId;
    private final BotSourceType sourceType;
    private final String displayName;
    private final String extractedText;
    private final String contentSha256;
    private final Integer questionId;
    private final Integer questionVersionNo;
    private final int addedByUserId;
    private BotSourceStatus status;
    private final String externalSourceId;
    private final LocalDateTime createdAt;
    private LocalDateTime removedAt;

    private BotSource(int sourceId, int botId, BotSourceType sourceType,
                      String displayName, String extractedText,
                      String contentSha256, Integer questionId,
                      Integer questionVersionNo, int addedByUserId,
                      BotSourceStatus status, String externalSourceId,
                      LocalDateTime createdAt, LocalDateTime removedAt) {
        if (sourceId < 0) {
            throw new IllegalArgumentException("Bot source ID cannot be negative");
        }
        requirePositive(botId, "Bot ID must be positive");
        requirePositive(addedByUserId, "Source actor ID must be positive");
        if (sourceType == null) {
            throw new IllegalArgumentException("Bot source type is required");
        }
        this.displayName = requireNonBlank(displayName, "Source display name is required");
        this.extractedText = requireNonBlank(extractedText, "Extracted source text is required");
        if (contentSha256 == null || !SHA256.matcher(contentSha256).matches()) {
            throw new IllegalArgumentException(
                    "Source checksum must be 64 lowercase hexadecimal characters"
            );
        }
        validateQuestionReference(sourceType, questionId, questionVersionNo);
        if (status == null) {
            throw new IllegalArgumentException("Bot source status is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Source creation time is required");
        }
        validateRemovalState(status, createdAt, removedAt);

        this.sourceId = sourceId;
        this.botId = botId;
        this.sourceType = sourceType;
        this.contentSha256 = contentSha256;
        this.questionId = questionId;
        this.questionVersionNo = questionVersionNo;
        this.addedByUserId = addedByUserId;
        this.status = status;
        this.externalSourceId = normalizeNullable(externalSourceId);
        this.createdAt = createdAt;
        this.removedAt = removedAt;
    }

    public static BotSource create(int botId, BotSourceType sourceType,
                                   String displayName, String extractedText,
                                   String contentSha256, Integer questionId,
                                   Integer questionVersionNo, int addedByUserId,
                                   String externalSourceId,
                                   LocalDateTime createdAt) {
        return new BotSource(
                0, botId, sourceType, displayName, extractedText,
                contentSha256, questionId, questionVersionNo, addedByUserId,
                BotSourceStatus.ACTIVE, externalSourceId, createdAt, null
        );
    }

    public static BotSource rehydrate(int sourceId, int botId,
                                      BotSourceType sourceType,
                                      String displayName, String extractedText,
                                      String contentSha256, Integer questionId,
                                      Integer questionVersionNo,
                                      int addedByUserId, BotSourceStatus status,
                                      String externalSourceId,
                                      LocalDateTime createdAt,
                                      LocalDateTime removedAt) {
        if (sourceId <= 0) {
            throw new IllegalArgumentException("Persisted Bot source ID must be positive");
        }
        return new BotSource(
                sourceId, botId, sourceType, displayName, extractedText,
                contentSha256, questionId, questionVersionNo, addedByUserId,
                status, externalSourceId, createdAt, removedAt
        );
    }

    public int getSourceId() { return sourceId; }
    public int getBotId() { return botId; }
    public BotSourceType getSourceType() { return sourceType; }
    public String getDisplayName() { return displayName; }
    /** Which revision of this source is in use; 1 until it is first edited. */
    private int currentVersionNo = 1;

    public int getCurrentVersionNo() { return currentVersionNo; }

    public void setCurrentVersionNo(int currentVersionNo) {
        if (currentVersionNo <= 0) {
            throw new IllegalArgumentException("Bot source version must be positive");
        }
        this.currentVersionNo = currentVersionNo;
    }

    public String getExtractedText() { return extractedText; }
    public String getContentSha256() { return contentSha256; }
    public Integer getQuestionId() { return questionId; }
    public Integer getQuestionVersionNo() { return questionVersionNo; }
    public int getAddedByUserId() { return addedByUserId; }
    public BotSourceStatus getStatus() { return status; }
    public String getExternalSourceId() { return externalSourceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRemovedAt() { return removedAt; }

    public void remove(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Source removal time is required");
        }
        if (timestamp.isBefore(createdAt)) {
            throw new IllegalArgumentException("Source removal time cannot precede creation");
        }
        if (status == BotSourceStatus.REMOVED) {
            if (timestamp.equals(removedAt)) {
                return;
            }
            throw new IllegalStateException("Bot source has already been removed");
        }
        status = BotSourceStatus.REMOVED;
        removedAt = timestamp;
    }

    BotSource copy() {
        return new BotSource(
                sourceId, botId, sourceType, displayName, extractedText,
                contentSha256, questionId, questionVersionNo, addedByUserId,
                status, externalSourceId, createdAt, removedAt
        );
    }

    private static void validateQuestionReference(BotSourceType type,
                                                  Integer questionId,
                                                  Integer versionNo) {
        if (type == BotSourceType.QUESTION_BANK) {
            if (questionId == null || questionId <= 0
                    || versionNo == null || versionNo <= 0) {
                throw new IllegalArgumentException(
                        "Question-bank source requires a positive question and version"
                );
            }
        } else if (questionId != null || versionNo != null) {
            throw new IllegalArgumentException(
                    "Non-question source cannot reference a question version"
            );
        }
    }

    private static void validateRemovalState(BotSourceStatus status,
                                             LocalDateTime createdAt,
                                             LocalDateTime removedAt) {
        if (status == BotSourceStatus.ACTIVE && removedAt != null) {
            throw new IllegalArgumentException("Active source cannot have a removal time");
        }
        if (status == BotSourceStatus.REMOVED && removedAt == null) {
            throw new IllegalArgumentException("Removed source requires a removal time");
        }
        if (removedAt != null && removedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Source removal time cannot precede creation");
        }
    }

    private static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
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
