package hsts.common;

import java.io.Serializable;

public final class ReportTargetPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int targetId;

    public ReportTargetPayload(int targetId) {
        this.targetId = targetId;
    }

    public int getTargetId() {
        return targetId;
    }
}
