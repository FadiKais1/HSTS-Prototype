package hsts.server.control;

import hsts.common.type.NotificationType;

public class NotificationService {
    private DatabaseService databaseService;

    public void sendNotification(int userId, NotificationType type, String message) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void sendExamOpenedNotification(int executionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void sendGradePublishedNotification(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void markAsRead(int notificationId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
