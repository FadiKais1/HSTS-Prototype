package hsts.common;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Request to release an approved exam from the drawer for execution.
 *
 * <p>The teacher chooses the four character execution code that students enter,
 * as the specification requires. The code may be omitted, in which case the
 * server generates one; that keeps older callers working unchanged.</p>
 */
public class ScheduleExamExecutionPayload implements Serializable {
    private static final long serialVersionUID = 2L;

    private final int examId;
    private final int examVersionNo;
    private final LocalDateTime openingTime;
    private final LocalDateTime closingTime;
    private final String executionCode;

    /** Schedules with a server generated execution code. */
    public ScheduleExamExecutionPayload(int examId, int examVersionNo,
                                        LocalDateTime openingTime,
                                        LocalDateTime closingTime) {
        this(examId, examVersionNo, openingTime, closingTime, null);
    }

    /** Schedules with the execution code chosen by the teacher. */
    public ScheduleExamExecutionPayload(int examId, int examVersionNo,
                                        LocalDateTime openingTime,
                                        LocalDateTime closingTime,
                                        String executionCode) {
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.executionCode = executionCode;
    }

    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public LocalDateTime getOpeningTime() { return openingTime; }
    public LocalDateTime getClosingTime() { return closingTime; }

    /** The teacher's chosen code, or {@code null} to let the server generate one. */
    public String getExecutionCode() { return executionCode; }
}
