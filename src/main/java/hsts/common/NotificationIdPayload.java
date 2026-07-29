package hsts.common;

import java.io.Serializable;

public final class NotificationIdPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int notificationId;

    public NotificationIdPayload(int notificationId) {
        this.notificationId = notificationId;
    }

    public int getNotificationId() { return notificationId; }
}
