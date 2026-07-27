package hsts.server.entity;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;

import java.util.List;
import java.util.Objects;

public class User {
    private int userId;
    private String fullName;
    private String email;
    private String passwordHash;
    private UserRole role;
    private UserStatus status;

    // RELATIONSHIP-DERIVED: User receives zero or more notifications.
    private List<Notification> notifications;

    // COMPATIBILITY-ONLY: Required by existing skeleton subclasses.
    protected User() {
        this.notifications = List.of();
    }

    public User(int userId, String fullName, String email, String passwordHash,
                UserRole role, UserStatus status) {
        this(userId, fullName, email, passwordHash, role, status, List.of());
    }

    protected User(int userId, String fullName, String email, String passwordHash,
                   UserRole role, UserStatus status,
                   List<Notification> notifications) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.notifications = immutableRelationshipCopy(
                notifications,
                "Notifications are required"
        );
    }

    public int getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

    public void updateProfile(String fullName, String email) {
        throw new UnsupportedOperationException("User profile data is externally managed");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    protected static <T> List<T> immutableRelationshipCopy(List<T> values,
                                                            String nullMessage) {
        return List.copyOf(Objects.requireNonNull(values, nullMessage));
    }
}
