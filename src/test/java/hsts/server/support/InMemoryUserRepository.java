package hsts.server.support;

import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import hsts.server.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class InMemoryUserRepository extends UserRepository {
    private final Map<Integer, User> users = new LinkedHashMap<>();
    private RuntimeException findByEmailFailure;
    private String lastEmailLookup;
    private int statusUpdateCalls;

    public InMemoryUserRepository(User... initialUsers) {
        for (User user : initialUsers) {
            users.put(user.getUserId(), user);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (findByEmailFailure != null) {
            throw findByEmailFailure;
        }
        if (email == null) {
            return Optional.empty();
        }

        lastEmailLookup = email;
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return users.values().stream()
                .filter(user -> user.getEmail().equals(normalizedEmail))
                .findFirst();
    }

    @Override
    public Optional<User> findById(int userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public boolean updateStatus(int userId, UserStatus status) {
        statusUpdateCalls++;
        User existingUser = users.get(userId);
        if (existingUser == null) {
            return false;
        }

        users.put(userId, new User(
                existingUser.getUserId(),
                existingUser.getFullName(),
                existingUser.getEmail(),
                existingUser.getPasswordHash(),
                existingUser.getRole(),
                status
        ));
        return true;
    }

    public User getUser(int userId) {
        return users.get(userId);
    }

    public String getLastEmailLookup() {
        return lastEmailLookup;
    }

    public int getStatusUpdateCalls() {
        return statusUpdateCalls;
    }

    public void setFindByEmailFailure(RuntimeException findByEmailFailure) {
        this.findByEmailFailure = findByEmailFailure;
    }
}
