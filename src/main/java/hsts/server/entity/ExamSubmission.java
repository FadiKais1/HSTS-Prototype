package hsts.server.entity;

import hsts.common.type.SubmissionStatus;

import java.time.LocalDateTime;

public class ExamSubmission {
    private int submissionId;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private SubmissionStatus status;
    private double finalScore;
    private String teacherFeedback;
    private double automaticScore;

    public double calculateFinalScore() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void changeSubmissionStatus(SubmissionStatus status) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
