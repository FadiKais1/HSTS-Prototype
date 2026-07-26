package hsts.server.entity;

import hsts.common.type.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.List;

public class ExamExecution {
    private int executionId;
    private String executionCode;
    private LocalDateTime openingTime;
    private LocalDateTime closingTime;
    private int durationMinutes;
    private ExecutionStatus status;
    private int extraMinutes;
    private String extensionReason;
    private double averageScore;
    private double medianScore;
    private List<Integer> decileDistribution;
    private int startedCount;
    private int submittedCount;
    private int autoSubmittedCount;

    // RELATIONSHIP-DERIVED: Exam execution contains zero or more submissions.
    private List<ExamSubmission> examSubmissions;

    public void openExecution() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void closeExecution() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateCode(String code) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean isOpen() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void extendTime(int extraMinutes, String reason) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateStatistics() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
