package hsts.server.repository;

import hsts.common.NotificationDTO;
import hsts.common.type.NotificationType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {
    private static final String LIST_SQL = """
            SELECT notification_id, notification_type, title, message,
                   related_exam_id, related_execution_id, related_submission_id,
                   created_at, read_at
            FROM notifications
            WHERE recipient_user_id = ?
            ORDER BY created_at DESC, notification_id DESC
            """;
    private static final String COUNT_SQL = """
            SELECT COUNT(*) AS unread_count
            FROM notifications
            WHERE recipient_user_id = ? AND read_at IS NULL
            """;
    private static final String MARK_READ_SQL = """
            UPDATE notifications
            SET read_at = ?
            WHERE notification_id = ?
              AND recipient_user_id = ?
              AND read_at IS NULL
            """;
    private static final String OWNED_SQL = """
            SELECT 1 FROM notifications
            WHERE notification_id = ? AND recipient_user_id = ?
            """;
    private static final String INSERT_SQL = """
            INSERT INTO notifications (
                recipient_user_id, notification_type, title, message,
                related_exam_id, related_execution_id, related_submission_id,
                deduplication_key, created_at, read_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
            ON DUPLICATE KEY UPDATE notification_id = notification_id
            """;

    private final DatabaseController databaseController;

    public NotificationRepository() {
        this(new DatabaseController());
    }

    public NotificationRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
    }

    public List<NotificationDTO> findForRecipient(int authenticatedUserId) {
        List<NotificationDTO> notifications = new ArrayList<>();
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(LIST_SQL)) {
            statement.setInt(1, authenticatedUserId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    notifications.add(map(rows));
                }
            }
            return List.copyOf(notifications);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load notifications", exception);
        }
    }

    public int countUnread(int authenticatedUserId) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_SQL)) {
            statement.setInt(1, authenticatedUserId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt("unread_count") : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to count unread notifications", exception);
        }
    }

    public boolean markRead(int authenticatedUserId, int notificationId,
                            LocalDateTime readAt) {
        try (Connection connection = databaseController.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(MARK_READ_SQL)) {
                statement.setObject(1, readAt);
                statement.setInt(2, notificationId);
                statement.setInt(3, authenticatedUserId);
                if (statement.executeUpdate() == 1) {
                    return true;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(OWNED_SQL)) {
                statement.setInt(1, notificationId);
                statement.setInt(2, authenticatedUserId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to mark notification as read", exception);
        }
    }

    static void insert(Connection connection, int recipientUserId,
                       NotificationType type, String title, String message,
                       Integer relatedExamId, Integer relatedExecutionId,
                       Integer relatedSubmissionId, String deduplicationKey,
                       LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setInt(1, recipientUserId);
            statement.setString(2, type.name());
            statement.setString(3, title);
            statement.setString(4, message);
            setNullableInt(statement, 5, relatedExamId);
            setNullableInt(statement, 6, relatedExecutionId);
            setNullableInt(statement, 7, relatedSubmissionId);
            statement.setString(8, deduplicationKey);
            statement.setObject(9, createdAt);
            statement.executeUpdate();
        }
    }

    private static NotificationDTO map(ResultSet rows) throws SQLException {
        return new NotificationDTO(
                rows.getInt("notification_id"),
                NotificationType.valueOf(rows.getString("notification_type")),
                rows.getString("title"),
                rows.getString("message"),
                rows.getObject("related_exam_id", Integer.class),
                rows.getObject("related_execution_id", Integer.class),
                rows.getObject("related_submission_id", Integer.class),
                rows.getObject("created_at", LocalDateTime.class),
                rows.getObject("read_at", LocalDateTime.class)
        );
    }

    private static void setNullableInt(PreparedStatement statement, int index,
                                       Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER);
        else statement.setInt(index, value);
    }
}
