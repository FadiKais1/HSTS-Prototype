package hsts.common;

import java.io.Serializable;

public final class ExtendExecutionTimePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int executionId;
    private final int addedMinutes;
    private final String reason;

    public ExtendExecutionTimePayload(int executionId, int addedMinutes, String reason) {
        this.executionId = executionId;
        this.addedMinutes = addedMinutes;
        this.reason = reason;
    }

    public int getExecutionId() { return executionId; }
    public int getAddedMinutes() { return addedMinutes; }
    public String getReason() { return reason; }
}
