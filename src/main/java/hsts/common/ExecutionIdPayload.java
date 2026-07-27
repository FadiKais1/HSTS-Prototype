package hsts.common;

import java.io.Serializable;

public class ExecutionIdPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int executionId;

    public ExecutionIdPayload(int executionId) {
        this.executionId = executionId;
    }

    public int getExecutionId() { return executionId; }
}
