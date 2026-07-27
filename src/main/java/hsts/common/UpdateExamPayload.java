package hsts.common;

import java.io.Serializable;
import java.util.List;

public class UpdateExamPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int examId;
    private final int expectedVersionNo;
    private final String title;
    private final int durationMinutes;
    private final String teacherNotes;
    private final String studentInstructions;
    private final List<ExamQuestionSelectionPayload> questions;

    public UpdateExamPayload(int examId, int expectedVersionNo, String title,
                             int durationMinutes, String teacherNotes,
                             String studentInstructions,
                             List<ExamQuestionSelectionPayload> questions) {
        this.examId = examId;
        this.expectedVersionNo = expectedVersionNo;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.teacherNotes = teacherNotes;
        this.studentInstructions = studentInstructions;
        this.questions = List.copyOf(questions);
    }

    public int getExamId() { return examId; }
    public int getExpectedVersionNo() { return expectedVersionNo; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getTeacherNotes() { return teacherNotes; }
    public String getStudentInstructions() { return studentInstructions; }
    public List<ExamQuestionSelectionPayload> getQuestions() { return questions; }
}
