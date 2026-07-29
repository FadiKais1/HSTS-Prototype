package hsts.server.control;

import hsts.common.NotificationDTO;
import hsts.common.type.NotificationType;
import hsts.server.entity.User;
import hsts.server.repository.NotificationRepository;
import hsts.server.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationService {
    private DatabaseService databaseService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public List<NotificationDTO> getMyNotifications(int authenticatedUserId) {
        requireActiveUser(authenticatedUserId);
        return List.copyOf(notificationRepository.findForRecipient(authenticatedUserId));
    }

    public int getUnreadCount(int authenticatedUserId) {
        requireActiveUser(authenticatedUserId);
        return notificationRepository.countUnread(authenticatedUserId);
    }

    public NotificationDTO markAsRead(int authenticatedUserId, int notificationId) {
        requireActiveUser(authenticatedUserId);
        if (notificationId <= 0) {
            throw new IllegalArgumentException("Notification not found");
        }
        if (!notificationRepository.markRead(
                authenticatedUserId,
                notificationId,
                LocalDateTime.now(clock)
        )) {
            throw new IllegalArgumentException("Notification not found");
        }
        return notificationRepository.findForRecipient(authenticatedUserId).stream()
                .filter(notification -> notification.getNotificationId() == notificationId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    }

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

    private User requireActiveUser(int authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + authenticatedUserId
                ));
        if (!user.isActive()) {
            throw new IllegalStateException("User account is blocked");
        }
        return user;
    }
}
