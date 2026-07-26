package hsts.common;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;

import java.io.Serializable;

public class QuestionFilterPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Integer courseId;
    private final Integer subjectId;
    private final String topic;
    private final DifficultyLevel difficulty;
    private final QuestionStatus status;

    public QuestionFilterPayload(Integer courseId, Integer subjectId, String topic,
                                 DifficultyLevel difficulty, QuestionStatus status) {
        this.courseId = courseId;
        this.subjectId = subjectId;
        this.topic = topic;
        this.difficulty = difficulty;
        this.status = status;
    }

    public Integer getCourseId() { return courseId; }
    public Integer getSubjectId() { return subjectId; }
    public String getTopic() { return topic; }
    public DifficultyLevel getDifficulty() { return difficulty; }
    public QuestionStatus getStatus() { return status; }
}
