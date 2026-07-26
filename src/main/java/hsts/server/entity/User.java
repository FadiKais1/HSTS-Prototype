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

    public void updateProfile(String fullName, String email) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean isActive() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
