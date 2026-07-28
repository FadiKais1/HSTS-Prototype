package hsts.common;

import java.io.Serializable;

public final class CreateCourseBotPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int courseId;
    private final String botName;

    public CreateCourseBotPayload(int courseId, String botName) {
        this.courseId = BotContractSupport.requirePositive(
                courseId, "Course ID must be positive"
        );
        this.botName = BotContractSupport.requireNonBlank(botName, "Bot name is required");
    }

    public int getCourseId() { return courseId; }
    public String getBotName() { return botName; }
}
