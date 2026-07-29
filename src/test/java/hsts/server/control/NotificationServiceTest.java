package hsts.server.control;

import hsts.common.NotificationDTO;
import hsts.common.type.NotificationType;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import hsts.server.repository.NotificationRepository;
import hsts.server.repository.UserRepository;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NotificationServiceTest {
    private static final LocalDateTime CREATED =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime READ =
            LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC
    );

    @Test
    public void listAndUnreadCountUseOnlyAuthenticatedActiveUser() {
        FakeNotificationRepository notifications = new FakeNotificationRepository(
                notification(2, null), notification(1, READ)
        );
        NotificationService service = service(activeUser(), notifications);

        List<NotificationDTO> result = service.getMyNotifications(1002);

        assertEquals(List.of(2, 1), result.stream()
                .map(NotificationDTO::getNotificationId).toList());
        assertEquals(1, service.getUnreadCount(1002));
        assertEquals(1002, notifications.lastRecipientId);
        assertThrows(UnsupportedOperationException.class,
                () -> result.add(notification(3, null)));
    }

    @Test
    public void markReadUsesOwnedIdentityAndAuthoritativeServerTime() {
        FakeNotificationRepository notifications =
                new FakeNotificationRepository(notification(2, null));
        NotificationDTO result = service(activeUser(), notifications)
                .markAsRead(1002, 2);

        assertTrue(result.isRead());
        assertEquals(READ, result.getReadAt());
        assertEquals(1002, notifications.lastRecipientId);
        assertEquals(2, notifications.lastNotificationId);
        assertEquals(READ, notifications.lastReadAt);
    }

    @Test
    public void missingOrUnownedNotificationUsesConcealedError() {
        FakeNotificationRepository notifications = new FakeNotificationRepository();
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> service(activeUser(), notifications).markAsRead(1002, 99)
        );
        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> service(activeUser(), notifications).markAsRead(1002, 0)
        );

        assertEquals("Notification not found", missing.getMessage());
        assertEquals("Notification not found", invalid.getMessage());
    }

    @Test
    public void blockedAndMissingUsersCannotReadNotifications() {
        FakeNotificationRepository notifications = new FakeNotificationRepository();
        User blocked = new User(
                1002, "Teacher", "teacher@example.test", "hash",
                UserRole.TEACHER, UserStatus.BLOCKED
        );
        assertEquals("User account is blocked", assertThrows(
                IllegalStateException.class,
                () -> service(blocked, notifications).getUnreadCount(1002)
        ).getMessage());
        assertEquals("User not found: 1002", assertThrows(
                IllegalArgumentException.class,
                () -> service(null, notifications).getMyNotifications(1002)
        ).getMessage());
        assertFalse(notifications.persistenceCalled);
    }

    private static NotificationService service(
            User user, FakeNotificationRepository notifications
    ) {
        return new NotificationService(
                notifications,
                new UserRepository() {
                    @Override
                    public Optional<User> findById(int userId) {
                        return Optional.ofNullable(user);
                    }
                },
                CLOCK
        );
    }

    private static User activeUser() {
        return new User(
                1002, "Teacher", "teacher@example.test", "hash",
                UserRole.TEACHER, UserStatus.ACTIVE
        );
    }

    private static NotificationDTO notification(int id, LocalDateTime readAt) {
        return new NotificationDTO(
                id, NotificationType.EXAM_APPROVED, "Exam approved",
                "Algebra Midterm was approved.", 40, null, null, CREATED, readAt
        );
    }

    private static final class FakeNotificationRepository
            extends NotificationRepository {
        private final List<NotificationDTO> values = new ArrayList<>();
        private int lastRecipientId;
        private int lastNotificationId;
        private LocalDateTime lastReadAt;
        private boolean persistenceCalled;

        private FakeNotificationRepository(NotificationDTO... initial) {
            values.addAll(List.of(initial));
        }

        @Override
        public List<NotificationDTO> findForRecipient(int authenticatedUserId) {
            persistenceCalled = true;
            lastRecipientId = authenticatedUserId;
            return List.copyOf(values);
        }

        @Override
        public int countUnread(int authenticatedUserId) {
            persistenceCalled = true;
            lastRecipientId = authenticatedUserId;
            return (int) values.stream().filter(value -> !value.isRead()).count();
        }

        @Override
        public boolean markRead(int authenticatedUserId, int notificationId,
                                LocalDateTime readAt) {
            persistenceCalled = true;
            lastRecipientId = authenticatedUserId;
            lastNotificationId = notificationId;
            lastReadAt = readAt;
            for (int index = 0; index < values.size(); index++) {
                NotificationDTO current = values.get(index);
                if (current.getNotificationId() == notificationId) {
                    values.set(index, new NotificationDTO(
                            current.getNotificationId(), current.getType(),
                            current.getTitle(), current.getMessage(),
                            current.getRelatedExamId(), current.getRelatedExecutionId(),
                            current.getRelatedSubmissionId(), current.getCreatedAt(), readAt
                    ));
                    return true;
                }
            }
            return false;
        }
    }
}
