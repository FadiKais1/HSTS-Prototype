package hsts.common;

import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ResponseStatus status;
    private final String message;
    private final Object payload;

    public Response(ResponseStatus status, String message, Object payload) {
        this.status = status;
        this.message = message;
        this.payload = payload;
    }

    public static Response success(String message, Object payload) {
        return new Response(ResponseStatus.SUCCESS, message, payload);
    }

    public static Response error(String message) {
        return new Response(ResponseStatus.ERROR, message, null);
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Object getPayload() {
        return payload;
    }

    public boolean isSuccess() {
        return status == ResponseStatus.SUCCESS;
    }
}
