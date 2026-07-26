package hsts.server.control;

import hsts.common.LoginRequestPayload;
import hsts.common.LoginResult;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import hsts.server.security.PasswordHasher;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AuthServiceTest {
    private static final String PASSWORD = "valid-test-password";
    private static final String PASSWORD_HASH = PasswordHasher.hash(PASSWORD);

    @Test
    public void correctLoginReturnsCompleteLoginResult() {
        AuthService service = serviceWithUser(activeUser());

        LoginResult result = service.login(new LoginRequestPayload(
                "student@hsts.local",
                PASSWORD
        ));

        assertEquals(1001, result.getUserId());
        assertEquals("Development Student", result.getFullName());
        assertEquals(UserRole.STUDENT, result.getRole());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getSessionId());
        assertEquals(result.getSessionId(), UUID.fromString(result.getSessionId()).toString());
    }

    @Test
    public void loginNormalizesEmailAndUmlOverloadReturnsUser() {
        InMemoryUserRepository repository = new InMemoryUserRepository(activeUser());
        AuthService service = new AuthService(repository);

        User result = service.login("  STUDENT@HSTS.LOCAL  ", PASSWORD);

        assertEquals(1001, result.getUserId());
        assertEquals("student@hsts.local", repository.getLastEmailLookup());
    }

    @Test
    public void wrongPasswordAndMissingUserUseSameError() {
        AuthService service = serviceWithUser(activeUser());

        IllegalArgumentException wrongPassword = assertThrows(
                IllegalArgumentException.class,
                () -> service.login("student@hsts.local", "wrong-password")
        );
        IllegalArgumentException missingUser = assertThrows(
                IllegalArgumentException.class,
                () -> service.login("missing@hsts.local", PASSWORD)
        );

        assertEquals("Invalid email or password", wrongPassword.getMessage());
        assertEquals(wrongPassword.getMessage(), missingUser.getMessage());
    }

    @Test
    public void blankCredentialsAndNullPayloadUseRequiredMessage() {
        AuthService service = serviceWithUser(activeUser());

        assertRequiredCredentialsMessage(() -> service.login(" ", PASSWORD));
        assertRequiredCredentialsMessage(() -> service.login("student@hsts.local", "\t"));
        assertRequiredCredentialsMessage(() -> service.login((LoginRequestPayload) null));
    }

    @Test
    public void blockedAccountCannotLogin() {
        AuthService service = serviceWithUser(blockedUser());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.login("student@hsts.local", PASSWORD)
        );

        assertEquals("User account is blocked", exception.getMessage());
    }

    @Test
    public void simultaneousDuplicateLoginAllowsOnlyOneSession() throws Exception {
        AuthService service = serviceWithUser(activeUser());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Object> first = executor.submit(() -> loginAfterSignal(service, ready, start));
            Future<Object> second = executor.submit(() -> loginAfterSignal(service, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);
            int successfulLogins = (firstResult instanceof User ? 1 : 0)
                    + (secondResult instanceof User ? 1 : 0);
            int duplicateErrors = isDuplicateError(firstResult) + isDuplicateError(secondResult);

            assertEquals(1, successfulLogins);
            assertEquals(1, duplicateErrors);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void logoutAllowsSuccessfulRelogin() {
        AuthService service = serviceWithUser(activeUser());

        User firstLogin = service.login("student@hsts.local", PASSWORD);
        service.logout(firstLogin.getUserId());
        User secondLogin = service.login("student@hsts.local", PASSWORD);

        assertEquals(firstLogin.getUserId(), secondLogin.getUserId());
    }

    @Test
    public void guardedLogoutRejectsWrongAndStaleSessionIds() {
        AuthService service = serviceWithUser(activeUser());
        LoginResult first = service.login(new LoginRequestPayload("student@hsts.local", PASSWORD));

        service.logout(first.getUserId(), "wrong-session-id");
        assertDuplicateLogin(service);

        service.logout(first.getUserId(), first.getSessionId());
        LoginResult second = service.login(new LoginRequestPayload("student@hsts.local", PASSWORD));

        service.logout(second.getUserId(), first.getSessionId());
        assertDuplicateLogin(service);

        service.logout(second.getUserId(), second.getSessionId());
        assertEquals(1001, service.login("student@hsts.local", PASSWORD).getUserId());
    }

    @Test
    public void validateCredentialsCreatesNoSession() {
        AuthService service = serviceWithUser(activeUser());

        assertTrue(service.validateCredentials(" STUDENT@HSTS.LOCAL ", PASSWORD));
        assertFalse(service.validateCredentials("student@hsts.local", "wrong-password"));
        assertFalse(service.validateCredentials("missing@hsts.local", PASSWORD));
        assertFalse(service.validateCredentials(" ", PASSWORD));
        assertFalse(service.validateCredentials("student@hsts.local", null));
        assertEquals(1001, service.login("student@hsts.local", PASSWORD).getUserId());
    }

    @Test
    public void validateCredentialsRejectsBlockedUser() {
        AuthService service = serviceWithUser(blockedUser());

        assertFalse(service.validateCredentials("student@hsts.local", PASSWORD));
    }

    @Test
    public void manualBlockUpdatesStatusAndReleasesSession() {
        InMemoryUserRepository repository = new InMemoryUserRepository(activeUser());
        AuthService service = new AuthService(repository);
        service.login("student@hsts.local", PASSWORD);

        service.blockLogin(1001);

        assertEquals(UserStatus.BLOCKED, repository.getUser(1001).getStatus());
        assertEquals(1, repository.getStatusUpdateCalls());

        repository.updateStatus(1001, UserStatus.ACTIVE);
        assertEquals(1001, service.login("student@hsts.local", PASSWORD).getUserId());
    }

    @Test
    public void blockMissingUserUsesExactError() {
        AuthService service = serviceWithUser();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.blockLogin(404)
        );

        assertEquals("User not found: 404", exception.getMessage());
    }

    @Test
    public void repositoryFailurePropagates() {
        InMemoryUserRepository repository = new InMemoryUserRepository(activeUser());
        IllegalStateException repositoryFailure = new IllegalStateException("repository failure");
        repository.setFindByEmailFailure(repositoryFailure);
        AuthService service = new AuthService(repository);

        IllegalStateException result = assertThrows(
                IllegalStateException.class,
                () -> service.validateCredentials("student@hsts.local", PASSWORD)
        );

        assertSame(repositoryFailure, result);
    }

    @Test
    public void validatePermissionRemainsUnsupported() {
        AuthService service = serviceWithUser(activeUser());

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> service.validatePermission(1001, "EDIT_EXAM")
        );

        assertEquals("Not implemented in Assignment 2 skeleton", exception.getMessage());
    }

    private static Object loginAfterSignal(AuthService service, CountDownLatch ready,
                                           CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return service.login("student@hsts.local", PASSWORD);
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private static int isDuplicateError(Object result) {
        return result instanceof IllegalStateException exception
                && "User is already logged in".equals(exception.getMessage()) ? 1 : 0;
    }

    private static void assertDuplicateLogin(AuthService service) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.login("student@hsts.local", PASSWORD)
        );
        assertEquals("User is already logged in", exception.getMessage());
    }

    private static void assertRequiredCredentialsMessage(Runnable loginAttempt) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                loginAttempt::run
        );
        assertEquals("Email and password are required", exception.getMessage());
    }

    private static AuthService serviceWithUser(User... users) {
        return new AuthService(new InMemoryUserRepository(users));
    }

    private static User activeUser() {
        return user(UserStatus.ACTIVE);
    }

    private static User blockedUser() {
        return user(UserStatus.BLOCKED);
    }

    private static User user(UserStatus status) {
        return new User(
                1001,
                "Development Student",
                "student@hsts.local",
                PASSWORD_HASH,
                UserRole.STUDENT,
                status
        );
    }
}
