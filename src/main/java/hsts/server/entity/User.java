package hsts.server.entity;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;

import java.util.List;

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
    }

    public User(int userId, String fullName, String email, String passwordHash,
                UserRole role, UserStatus status) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
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

    public void updateProfile(String fullName, String email) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
