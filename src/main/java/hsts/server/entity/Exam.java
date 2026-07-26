package hsts.server.entity;

import hsts.common.type.ExamStatus;

import java.time.LocalDateTime;
import java.util.List;

public class Exam {
    private int examId;
    private String title;
    private int durationMinutes;
    private double totalScore;
    private ExamStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    // RELATIONSHIP-DERIVED: Exam contains one or more exam questions.
    private List<ExamQuestion> examQuestions;

    // RELATIONSHIP-DERIVED: Exam has zero or more exam executions.
    private List<ExamExecution> examExecutions;

    public double calculateTotalScore() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void addExamQuestion(ExamQuestion examQuestion) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void setExamNotes(String teacherNotes, String studentNotes) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
