package hsts.common;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;

import java.io.Serializable;

public class LoginResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int userId;
    private final String fullName;
    private final UserRole role;
    private final UserStatus status;
    private final String sessionId;

    public LoginResult(int userId, String fullName, UserRole role,
                       UserStatus status, String sessionId) {
        this.userId = userId;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.sessionId = sessionId;
    }

    public int getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getSessionId() {
        return sessionId;
    }
}
