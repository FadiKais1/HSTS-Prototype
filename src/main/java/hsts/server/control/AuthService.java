package hsts.server.control;

import hsts.common.LoginRequestPayload;
import hsts.common.LoginResult;
import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import hsts.server.repository.UserRepository;
import hsts.server.security.PasswordHasher;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AuthService {
    private DatabaseService databaseService;

    private final UserRepository userRepository;
    private final ConcurrentMap<Integer, String> activeSessions;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.activeSessions = new ConcurrentHashMap<>();
    }

    public User login(String email, String password) {
        return authenticateAndOpenSession(email, password).user();
    }

    public void logout(int userId) {
        activeSessions.remove(userId);
    }

    public boolean validateCredentials(String email, String password) {
        if (isBlank(email) || isBlank(password)) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
                .filter(User::isActive)
                .map(user -> PasswordHasher.matches(password, user.getPasswordHash()))
                .orElse(false);
    }

    public boolean validatePermission(int userId, String action) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void blockLogin(int userId) {
        if (userRepository.findById(userId).isEmpty()
                || !userRepository.updateStatus(userId, UserStatus.BLOCKED)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        activeSessions.remove(userId);
    }

    public LoginResult login(LoginRequestPayload request) {
        AuthenticatedSession authenticatedSession = authenticateAndOpenSession(
                request == null ? null : request.getEmail(),
                request == null ? null : request.getPassword()
        );
        User user = authenticatedSession.user();

        return new LoginResult(
                user.getUserId(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                authenticatedSession.sessionId()
        );
    }

    public void logout(int userId, String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(userId, sessionId);
        }
    }

    public boolean isSessionActive(int userId, String sessionId) {
        return sessionId != null && sessionId.equals(activeSessions.get(userId));
    }

    private AuthenticatedSession authenticateAndOpenSession(String email, String password) {
        if (isBlank(email) || isBlank(password)) {
            throw new IllegalArgumentException("Email and password are required");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("User account is blocked");
        }

        String sessionId = UUID.randomUUID().toString();
        if (activeSessions.putIfAbsent(user.getUserId(), sessionId) != null) {
            throw new IllegalStateException("User is already logged in");
        }

        return new AuthenticatedSession(user, sessionId);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record AuthenticatedSession(User user, String sessionId) {
    }
}
