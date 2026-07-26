package hsts.server.entity;

import hsts.common.type.NotificationType;

import java.time.LocalDateTime;

public class Notification {
    private int notificationId;
    private NotificationType type;
    private String message;
    private LocalDateTime sentAt;
    private boolean isRead;

    public void markAsRead() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateMessage(String message) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
