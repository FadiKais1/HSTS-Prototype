package hsts.common;

import java.io.Serializable;

public class StartExamPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int executionId;
    private final String identityConfirmation;

    public StartExamPayload(int executionId, String identityConfirmation) {
        this.executionId = executionId;
        this.identityConfirmation = identityConfirmation;
    }

    public int getExecutionId() { return executionId; }
    public String getIdentityConfirmation() { return identityConfirmation; }
}
