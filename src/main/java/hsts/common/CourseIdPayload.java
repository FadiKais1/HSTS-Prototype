package hsts.common;

import java.io.Serializable;

public final class CourseIdPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int courseId;

    public CourseIdPayload(int courseId) {
        this.courseId = BotContractSupport.requirePositive(
                courseId, "Course ID must be positive"
        );
    }

    public int getCourseId() { return courseId; }
}
