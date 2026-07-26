package hsts.server.control;

import hsts.server.entity.Exam;
import hsts.server.entity.ExamExecution;
import hsts.server.entity.ExamSubmission;

import java.time.LocalDateTime;

public class ExamExecutionService {
    private DatabaseService databaseService;

    public ExamExecution createExamExecution(int examId, LocalDateTime openingTime, LocalDateTime closingTime) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void closeExamExecution(int executionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateExecutionCode(String code) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public ExamSubmission startExam(int studentId, Exam exam) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveAnswer(int submissionId, int questionId, int answerChoice) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void submitExam(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void autoSubmit(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void extendTime(int submissionId, int extraMinutes, String reason) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateStudentId(int studentId, int id) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void startTime(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void calculateRemainingTime(int durationMinutes, LocalDateTime currentTime, LocalDateTime startedAt) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
