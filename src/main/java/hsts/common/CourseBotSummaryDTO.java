package hsts.common;

import hsts.common.type.BotStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public final class CourseBotSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final int courseId;
    private final String botName;
    private final String courseName;
    private final BotStatus status;
    private final int activeSourceCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CourseBotSummaryDTO(int botId, int courseId, String botName,
                               String courseName, BotStatus status,
                               int activeSourceCount, LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        this.courseId = BotContractSupport.requirePositive(
                courseId, "Course ID must be positive"
        );
        this.botName = BotContractSupport.requireNonBlank(botName, "Bot name is required");
        this.courseName = BotContractSupport.requireNonBlank(
                courseName, "Course name is required"
        );
        if (status == null) {
            throw new IllegalArgumentException("Bot status is required");
        }
        this.status = status;
        this.activeSourceCount = BotContractSupport.requireNonNegative(
                activeSourceCount, "Active source count cannot be negative"
        );
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Bot timestamps are required");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Bot update time cannot precede creation");
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getBotId() { return botId; }
    public int getCourseId() { return courseId; }
    public String getBotName() { return botName; }
    public String getCourseName() { return courseName; }
    public BotStatus getStatus() { return status; }
    public int getActiveSourceCount() { return activeSourceCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
