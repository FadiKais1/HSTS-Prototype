package hsts.common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public final class BotUsageSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final int courseId;
    private final String botName;
    private final String courseName;
    private final int totalQuestions;
    private final LocalDateTime lastActivityAt;
    private final List<CommonBotQuestionDTO> commonQuestions;

    public BotUsageSummaryDTO(int botId, int courseId, String botName,
                              String courseName, int totalQuestions,
                              LocalDateTime lastActivityAt,
                              List<CommonBotQuestionDTO> commonQuestions) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        this.courseId = BotContractSupport.requirePositive(
                courseId, "Course ID must be positive"
        );
        this.botName = BotContractSupport.requireNonBlank(botName, "Bot name is required");
        this.courseName = BotContractSupport.requireNonBlank(
                courseName, "Course name is required"
        );
        this.totalQuestions = BotContractSupport.requireNonNegative(
                totalQuestions, "Total question count cannot be negative"
        );
        this.commonQuestions = BotContractSupport.immutableList(
                commonQuestions,
                "Common Bot questions are required",
                "Common Bot questions cannot contain null"
        );
        if (totalQuestions == 0 && (lastActivityAt != null || !this.commonQuestions.isEmpty())) {
            throw new IllegalArgumentException(
                    "Empty Bot usage cannot have activity or common questions"
            );
        }
        this.lastActivityAt = lastActivityAt;
    }

    public int getBotId() { return botId; }
    public int getCourseId() { return courseId; }
    public String getBotName() { return botName; }
    public String getCourseName() { return courseName; }
    public int getTotalQuestions() { return totalQuestions; }
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public List<CommonBotQuestionDTO> getCommonQuestions() { return commonQuestions; }
}
