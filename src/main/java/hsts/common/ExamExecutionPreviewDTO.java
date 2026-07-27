package hsts.common;

import hsts.common.type.ExecutionStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ExamExecutionPreviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int executionId;
    private final String executionCode;
    private final int examId;
    private final int examVersionNo;
    private final String examTitle;
    private final int courseId;
    private final String courseName;
    private final LocalDateTime openingTime;
    private final LocalDateTime closingTime;
    private final int durationMinutes;
    private final ExecutionStatus status;
    private final boolean resumable;

    public ExamExecutionPreviewDTO(int executionId, String executionCode,
                                   int examId, int examVersionNo,
                                   String examTitle, int courseId,
                                   String courseName, LocalDateTime openingTime,
                                   LocalDateTime closingTime, int durationMinutes,
                                   ExecutionStatus status, boolean resumable) {
        this.executionId = executionId;
        this.executionCode = executionCode;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.examTitle = examTitle;
        this.courseId = courseId;
        this.courseName = courseName;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.durationMinutes = durationMinutes;
        this.status = status;
        this.resumable = resumable;
    }

    public int getExecutionId() { return executionId; }
    public String getExecutionCode() { return executionCode; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamTitle() { return examTitle; }
    public int getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public LocalDateTime getOpeningTime() { return openingTime; }
    public LocalDateTime getClosingTime() { return closingTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public ExecutionStatus getStatus() { return status; }
    public boolean isResumable() { return resumable; }
}
