package hsts.common;

import java.io.Serializable;
import java.util.List;

public final class BotHistoryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final int courseId;
    private final String botName;
    private final String courseName;
    private final List<BotMessageDTO> messages;

    public BotHistoryDTO(int botId, int courseId, String botName,
                         String courseName, List<BotMessageDTO> messages) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        this.courseId = BotContractSupport.requirePositive(
                courseId, "Course ID must be positive"
        );
        this.botName = BotContractSupport.requireNonBlank(botName, "Bot name is required");
        this.courseName = BotContractSupport.requireNonBlank(
                courseName, "Course name is required"
        );
        this.messages = BotContractSupport.immutableList(
                messages,
                "Bot history messages are required",
                "Bot history messages cannot contain null"
        );
    }

    public int getBotId() { return botId; }
    public int getCourseId() { return courseId; }
    public String getBotName() { return botName; }
    public String getCourseName() { return courseName; }
    public List<BotMessageDTO> getMessages() { return messages; }
}
