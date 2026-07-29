package hsts.common;

import hsts.common.type.NotificationType;

import java.io.Serializable;
import java.time.LocalDateTime;

public final class NotificationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int notificationId;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final Integer relatedExamId;
    private final Integer relatedExecutionId;
    private final Integer relatedSubmissionId;
    private final LocalDateTime createdAt;
    private final LocalDateTime readAt;

    public NotificationDTO(int notificationId, NotificationType type, String title,
                           String message, Integer relatedExamId,
                           Integer relatedExecutionId, Integer relatedSubmissionId,
                           LocalDateTime createdAt, LocalDateTime readAt) {
        this.notificationId = notificationId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.relatedExamId = relatedExamId;
        this.relatedExecutionId = relatedExecutionId;
        this.relatedSubmissionId = relatedSubmissionId;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public int getNotificationId() { return notificationId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Integer getRelatedExamId() { return relatedExamId; }
    public Integer getRelatedExecutionId() { return relatedExecutionId; }
    public Integer getRelatedSubmissionId() { return relatedSubmissionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public boolean isRead() { return readAt != null; }
}
