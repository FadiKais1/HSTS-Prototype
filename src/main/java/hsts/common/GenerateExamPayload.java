package hsts.common;

import hsts.common.type.DifficultyLevel;

import java.io.Serializable;

public class GenerateExamPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int courseId;
    private final String title;
    private final int durationMinutes;
    private final String teacherNotes;
    private final String studentInstructions;
    private final String topic;
    private final DifficultyLevel difficulty;
    private final int questionCount;

    public GenerateExamPayload(int courseId, String title, int durationMinutes,
                               String teacherNotes, String studentInstructions,
                               String topic, DifficultyLevel difficulty,
                               int questionCount) {
        this.courseId = courseId;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.teacherNotes = teacherNotes;
        this.studentInstructions = studentInstructions;
        this.topic = topic;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
    }

    public int getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getTeacherNotes() { return teacherNotes; }
    public String getStudentInstructions() { return studentInstructions; }
    public String getTopic() { return topic; }
    public DifficultyLevel getDifficulty() { return difficulty; }
    public int getQuestionCount() { return questionCount; }
}
