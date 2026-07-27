package hsts.common;

import java.io.Serializable;

public class ExecutionCodePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String executionCode;

    public ExecutionCodePayload(String executionCode) {
        this.executionCode = executionCode;
    }

    public String getExecutionCode() { return executionCode; }
}
