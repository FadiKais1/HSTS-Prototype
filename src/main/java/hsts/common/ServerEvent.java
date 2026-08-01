package hsts.common;

import java.io.Serializable;
import java.util.Objects;

/**
 * An unsolicited message pushed from the server to connected clients when
 * server-side state changes.
 *
 * <p>This type is deliberately distinct from {@link Response}. The client
 * transport correlates every {@code Response} with an outstanding request, so
 * pushed events must not travel as responses. Screens subscribe to these
 * events and refresh themselves, which removes the need for user-initiated
 * screen refreshes.</p>
 */
public final class ServerEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ServerEventType type;
    private final int referenceId;

    public ServerEvent(ServerEventType type, int referenceId) {
        this.type = Objects.requireNonNull(type, "Event type is required");
        this.referenceId = referenceId;
    }

    public ServerEventType getType() {
        return type;
    }

    /**
     * Identifier of the affected entity, or {@code 0} when the event is not
     * scoped to a single entity. The meaning depends on {@link #getType()}:
     * an execution identifier for execution-scoped events, otherwise unset.
     */
    public int getReferenceId() {
        return referenceId;
    }

    @Override
    public String toString() {
        return "ServerEvent{" + type + ", referenceId=" + referenceId + '}';
    }
}
