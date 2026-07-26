package hsts.common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private int requestId;
    private final RequestType type;
    private int userId;
    private final Object payload;
    private LocalDateTime createdAt;

    public Request(RequestType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public RequestType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }
}
