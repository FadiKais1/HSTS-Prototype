package hsts.common;

import hsts.common.type.ExecutionStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ExamExecutionSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int executionId;
    private final String executionCode;
    private final int examId;
    private final int examVersionNo;
    private final String examCode;
    private final String examTitle;
    private final int courseId;
    private final String courseName;
    private final LocalDateTime openingTime;
    private final LocalDateTime closingTime;
    private final int durationMinutes;
    private final ExecutionStatus status;
    private final int createdByUserId;
    private final String creatorName;
    private final LocalDateTime createdAt;
    private final Integer startedCount;
    private final Integer submittedCount;
    private final Integer autoSubmittedCount;

    public ExamExecutionSummaryDTO(int executionId, String executionCode,
                                   int examId, int examVersionNo,
                                   String examCode, String examTitle,
                                   int courseId, String courseName,
                                   LocalDateTime openingTime,
                                   LocalDateTime closingTime,
                                   int durationMinutes, ExecutionStatus status,
                                   int createdByUserId, String creatorName,
                                   LocalDateTime createdAt, Integer startedCount,
                                   Integer submittedCount,
                                   Integer autoSubmittedCount) {
        this.executionId = executionId;
        this.executionCode = executionCode;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.examCode = examCode;
        this.examTitle = examTitle;
        this.courseId = courseId;
        this.courseName = courseName;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.durationMinutes = durationMinutes;
        this.status = status;
        this.createdByUserId = createdByUserId;
        this.creatorName = creatorName;
        this.createdAt = createdAt;
        this.startedCount = startedCount;
        this.submittedCount = submittedCount;
        this.autoSubmittedCount = autoSubmittedCount;
    }

    public int getExecutionId() { return executionId; }
    public String getExecutionCode() { return executionCode; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamCode() { return examCode; }
    public String getExamTitle() { return examTitle; }
    public int getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public LocalDateTime getOpeningTime() { return openingTime; }
    public LocalDateTime getClosingTime() { return closingTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public ExecutionStatus getStatus() { return status; }
    public int getCreatedByUserId() { return createdByUserId; }
    public String getCreatorName() { return creatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Integer getStartedCount() { return startedCount; }
    public Integer getSubmittedCount() { return submittedCount; }
    public Integer getAutoSubmittedCount() { return autoSubmittedCount; }
}
