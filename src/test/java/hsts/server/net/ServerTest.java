package hsts.server.net;

import hsts.common.LoginRequestPayload;
import hsts.common.LoginResult;
import hsts.common.QuestionDTO;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.control.AuthService;
import hsts.server.control.ExamManagementService;
import hsts.server.entity.Question;
import hsts.server.entity.User;
import hsts.server.security.PasswordHasher;
import hsts.server.support.InMemoryQuestionRepository;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerTest {
    private static final String PASSWORD = "valid-test-password";
    private static final String PASSWORD_HASH = PasswordHasher.hash(PASSWORD);

    @Test
    public void getAllQuestionsReturnsExactSuccessResponse() {
        Server server = serverWith(question(1, "First"), question(2, "Second"));

        Response response = server.handleRequest(new Request(RequestType.GET_ALL_QUESTIONS, null));

        assertSuccess(response, "Questions loaded successfully");
        List<?> payload = (List<?>) response.getPayload();
        assertEquals(2, payload.size());
        assertEquals("First", ((QuestionDTO) payload.get(0)).getContent());
        assertEquals("Second", ((QuestionDTO) payload.get(1)).getContent());
    }

    @Test
    public void getQuestionByIdReturnsExactSuccessResponse() {
        Server server = serverWith(question(8, "Requested"));

        Response response = server.handleRequest(new Request(RequestType.GET_QUESTION_BY_ID, 8));

        assertSuccess(response, "Question loaded successfully");
        QuestionDTO payload = (QuestionDTO) response.getPayload();
        assertEquals(8, payload.getQuestionId());
        assertEquals("Requested", payload.getContent());
    }

    @Test
    public void updateQuestionReturnsExactSuccessResponse() {
        Server server = serverWith(question(9, "Old"));
        UpdateQuestionPayload payload = new UpdateQuestionPayload(
                9, "Updated", "Topic", "HARD", "ACTIVE", "",
                "One", "Two", "Three", "Four", 4
        );

        Response response = server.handleRequest(new Request(RequestType.UPDATE_QUESTION, payload));

        assertSuccess(response, "Question updated successfully");
        QuestionDTO result = (QuestionDTO) response.getPayload();
        assertEquals("Updated", result.getContent());
        assertEquals(4, result.getCorrectOptionNumber());
    }

    @Test
    public void serviceExceptionIsConvertedToExactErrorResponse() {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository();
        repository.setFindAllFailure(new IllegalStateException("Repository unavailable"));
        Server server = new Server(
                0,
                new ExamManagementService(repository),
                new AuthService(new InMemoryUserRepository())
        );

        Response response = server.handleRequest(new Request(RequestType.GET_ALL_QUESTIONS, null));

        assertError(response, "Repository unavailable");
    }

    @Test
    public void invalidPayloadIsConvertedToExactErrorResponse() {
        Server server = serverWith(question(10, "Old"));

        Response response = server.handleRequest(new Request(RequestType.UPDATE_QUESTION, null));

        assertError(response, "Question update data is missing");
    }

    @Test
    public void contextFreeLoginReturnsExactSuccessResponseAndLoginResult() {
        Server server = serverWithUsers(user(UserStatus.ACTIVE));

        Response response = server.handleRequest(new Request(
                RequestType.LOGIN,
                new LoginRequestPayload("student@hsts.local", PASSWORD)
        ));

        assertSuccess(response, "Login successful");
        LoginResult result = (LoginResult) response.getPayload();
        assertEquals(1001, result.getUserId());
        assertEquals("Development Student", result.getFullName());
        assertEquals(UserRole.STUDENT, result.getRole());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
        assertTrue(result.getSessionId() != null && !result.getSessionId().isBlank());
    }

    @Test
    public void contextFreeLoginRejectsInvalidPayload() {
        Server server = serverWithUsers(user(UserStatus.ACTIVE));

        Response response = server.handleRequest(new Request(RequestType.LOGIN, null));

        assertError(response, "Login request data is required");
    }

    @Test
    public void contextFreeLoginConvertsWrongCredentialsAndBlockedAccountToErrors() {
        Server activeUserServer = serverWithUsers(user(UserStatus.ACTIVE));
        Response wrongPassword = activeUserServer.handleRequest(new Request(
                RequestType.LOGIN,
                new LoginRequestPayload("student@hsts.local", "wrong-password")
        ));

        Server blockedUserServer = serverWithUsers(user(UserStatus.BLOCKED));
        Response blockedAccount = blockedUserServer.handleRequest(new Request(
                RequestType.LOGIN,
                new LoginRequestPayload("student@hsts.local", PASSWORD)
        ));

        assertError(wrongPassword, "Invalid email or password");
        assertError(blockedAccount, "User account is blocked");
    }

    @Test
    public void contextFreeLogoutRequiresConnectionContext() {
        Server server = serverWithUsers(user(UserStatus.ACTIVE));

        Response response = server.handleRequest(new Request(RequestType.LOGOUT, null));

        assertError(response, "Connection context required");
    }

    private static Server serverWith(Question... questions) {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository(questions);
        return new Server(
                0,
                new ExamManagementService(repository),
                new AuthService(new InMemoryUserRepository())
        );
    }

    private static Server serverWithUsers(User... users) {
        return new Server(
                0,
                new ExamManagementService(new InMemoryQuestionRepository()),
                new AuthService(new InMemoryUserRepository(users))
        );
    }

    private static Question question(int id, String content) {
        return new Question(
                id, content, "Topic", "MULTIPLE_CHOICE", "MEDIUM", "ACTIVE", "",
                "One", "Two", "Three", "Four", 1
        );
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

    private static void assertSuccess(Response response, String message) {
        assertTrue(response.isSuccess());
        assertEquals(ResponseStatus.SUCCESS, response.getStatus());
        assertEquals(message, response.getMessage());
    }

    private static void assertError(Response response, String message) {
        assertFalse(response.isSuccess());
        assertEquals(ResponseStatus.ERROR, response.getStatus());
        assertEquals(message, response.getMessage());
        assertNull(response.getPayload());
    }
}
