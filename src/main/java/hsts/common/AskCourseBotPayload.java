package hsts.common;

import java.io.Serializable;

public final class AskCourseBotPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int courseId;
    private final String questionText;

    public AskCourseBotPayload(int courseId, String questionText) {
        this.courseId = BotContractSupport.requirePositive(
                courseId, "Course ID must be positive"
        );
        this.questionText = BotContractSupport.requireNonBlank(
                questionText, "Bot question is required"
        );
    }

    public int getCourseId() { return courseId; }
    public String getQuestionText() { return questionText; }
}
