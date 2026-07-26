package hsts.server.control;

import hsts.server.entity.ExamSubmission;

import java.util.List;

public class GradingService {
    private DatabaseService databaseService;

    public double calculateAutomaticGrade(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateManualGrade(int submissionId, double score, String feedback) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void publishGrade(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public ExamSubmission getSubmissionResult(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getResultsByExam(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getResultsByStudent(int studentId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
