package hsts.common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ScheduleExamExecutionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int examId;
    private final int examVersionNo;
    private final LocalDateTime openingTime;
    private final LocalDateTime closingTime;

    public ScheduleExamExecutionPayload(int examId, int examVersionNo,
                                        LocalDateTime openingTime,
                                        LocalDateTime closingTime) {
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
    }

    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public LocalDateTime getOpeningTime() { return openingTime; }
    public LocalDateTime getClosingTime() { return closingTime; }
}
