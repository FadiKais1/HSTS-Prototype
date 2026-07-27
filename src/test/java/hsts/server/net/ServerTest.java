package hsts.server.net;

import hsts.common.CourseSummaryDTO;
import hsts.common.CreateQuestionPayload;
import hsts.common.LoginRequestPayload;
import hsts.common.LoginResult;
import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.control.AuthService;
import hsts.server.control.ExamManagementService;
import hsts.server.entity.Question;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.security.PasswordHasher;
import hsts.server.support.InMemoryQuestionRepository;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

    @Test
    public void authenticatedTeacherAndCoordinatorCanUseQuestionBankRoutes() {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.COORDINATOR}) {
            int authenticatedUserId = role == UserRole.TEACHER ? 2001 : 2002;
            TestCourseRepository courses = new TestCourseRepository();
            CourseSummaryDTO course = new CourseSummaryDTO(
                    7, 3, "MATH-7", "Mathematics", "Mathematics", "7", "2026"
            );
            courses.assign(authenticatedUserId, course);

            TestQuestionRepository questions = new TestQuestionRepository();
            QuestionDTO existingQuestion = normalizedQuestion(41, 7, "Existing question");
            questions.setQuestions(List.of(existingQuestion));
            Server server = questionBankServer(
                    questions,
                    courses,
                    user(authenticatedUserId, role, UserStatus.ACTIVE)
            );

            Response courseResponse = server.handleAuthenticatedRequest(
                    new Request(RequestType.GET_MY_COURSES, "ignored"),
                    authenticatedUserId
            );
            assertSuccess(courseResponse, "Courses loaded successfully");
            assertSame(course, ((List<?>) courseResponse.getPayload()).get(0));

            QuestionFilterPayload filter = new QuestionFilterPayload(
                    7, 3, "algebra", DifficultyLevel.MEDIUM, QuestionStatus.ACTIVE
            );
            Response listResponse = server.handleAuthenticatedRequest(
                    new Request(RequestType.LIST_QUESTIONS, filter),
                    authenticatedUserId
            );
            assertSuccess(listResponse, "Questions loaded successfully");
            assertSame(existingQuestion, ((List<?>) listResponse.getPayload()).get(0));
            assertEquals(authenticatedUserId, questions.getLastListUserId());
            assertSame(filter, questions.getLastFilter());

            CreateQuestionPayload payload = createPayload(7);
            Response createResponse = server.handleAuthenticatedRequest(
                    new Request(RequestType.CREATE_QUESTION, payload),
                    authenticatedUserId
            );
            assertSuccess(createResponse, "Question created successfully");
            assertEquals(501, ((QuestionDTO) createResponse.getPayload()).getQuestionId());
            assertEquals(authenticatedUserId, questions.getLastCreateUserId());
        }
    }

    @Test
    public void studentAndPrincipalQuestionBankRequestsAreRejected() {
        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.PRINCIPAL}) {
            int authenticatedUserId = role == UserRole.STUDENT ? 3001 : 3002;
            Server server = questionBankServer(
                    new TestQuestionRepository(),
                    new TestCourseRepository(),
                    user(authenticatedUserId, role, UserStatus.ACTIVE)
            );

            Response response = server.handleAuthenticatedRequest(
                    new Request(RequestType.GET_MY_COURSES, null),
                    authenticatedUserId
            );

            assertError(response, "Question management requires teacher or coordinator role");
        }
    }

    @Test
    public void questionListAcceptsNullFilterAndRejectsInvalidPayload() {
        int authenticatedUserId = 4001;
        TestQuestionRepository questions = new TestQuestionRepository();
        Server server = questionBankServer(
                questions,
                new TestCourseRepository(),
                user(authenticatedUserId, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        Response nullFilter = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_QUESTIONS, null),
                authenticatedUserId
        );
        Response invalidFilter = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_QUESTIONS, "not a filter"),
                authenticatedUserId
        );

        assertSuccess(nullFilter, "Questions loaded successfully");
        assertNull(questions.getLastFilter());
        assertError(invalidFilter, "Question filter data is invalid");
    }

    @Test
    public void createQuestionRejectsNullAndInvalidPayloads() {
        Server server = questionBankServer(
                new TestQuestionRepository(),
                new TestCourseRepository(),
                user(5001, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        Response nullPayload = server.handleAuthenticatedRequest(
                new Request(RequestType.CREATE_QUESTION, null),
                5001
        );
        Response invalidPayload = server.handleAuthenticatedRequest(
                new Request(RequestType.CREATE_QUESTION, "not question data"),
                5001
        );

        assertError(nullPayload, "Question data is required");
        assertError(invalidPayload, "Question data is required");
    }

    @Test
    public void questionBankServiceExceptionsAreConvertedToErrors() {
        TestCourseRepository courses = new TestCourseRepository();
        courses.setFindFailure(new IllegalStateException("Assigned courses unavailable"));
        Server server = questionBankServer(
                new TestQuestionRepository(),
                courses,
                user(6001, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        Response response = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_MY_COURSES, null),
                6001
        );

        assertError(response, "Assigned courses unavailable");
    }

    @Test
    public void contextFreeQuestionBankRoutesRequireAuthenticationContext() {
        Server server = serverWith();

        for (RequestType type : new RequestType[]{
                RequestType.GET_MY_COURSES,
                RequestType.LIST_QUESTIONS,
                RequestType.CREATE_QUESTION
        }) {
            Response response = server.handleRequest(new Request(type, null));
            assertError(response, "Authentication context required");
        }
    }

    @Test
    public void authenticatedLegacyQuestionRoutesUseScopedServiceMethods() {
        int authenticatedUserId = 7001;
        RecordingScopedExamManagementService service = new RecordingScopedExamManagementService();
        QuestionDTO listedQuestion = normalizedQuestion(61, 7, "Listed question");
        QuestionDTO selectedQuestion = normalizedQuestion(62, 7, "Selected question");
        QuestionDTO updatedQuestion = normalizedQuestion(63, 7, "Updated question");
        service.setListedQuestions(List.of(listedQuestion));
        service.setSelectedQuestion(selectedQuestion);
        service.setUpdatedQuestion(updatedQuestion);
        Server server = new Server(
                0,
                service,
                new AuthService(new InMemoryUserRepository())
        );
        UpdateQuestionPayload updatePayload = new UpdateQuestionPayload(
                63, "Updated question", "Topic", "HARD", "ACTIVE", "",
                "One", "Two", "Three", "Four", 2, 4
        );

        Response listResponse = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_ALL_QUESTIONS, null),
                authenticatedUserId
        );
        Response getResponse = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_QUESTION_BY_ID, 62),
                authenticatedUserId
        );
        Response updateResponse = server.handleAuthenticatedRequest(
                new Request(RequestType.UPDATE_QUESTION, updatePayload),
                authenticatedUserId
        );

        assertSuccess(listResponse, "Questions loaded successfully");
        assertSame(listedQuestion, ((List<?>) listResponse.getPayload()).get(0));
        assertEquals(authenticatedUserId, service.getLastListUserId());
        assertNull(service.getLastListFilter());

        assertSuccess(getResponse, "Question loaded successfully");
        assertSame(selectedQuestion, getResponse.getPayload());
        assertEquals(authenticatedUserId, service.getLastGetUserId());
        assertEquals(62, service.getLastQuestionId());

        assertSuccess(updateResponse, "Question updated successfully");
        assertSame(updatedQuestion, updateResponse.getPayload());
        assertEquals(authenticatedUserId, service.getLastUpdateUserId());
        assertSame(updatePayload, service.getLastUpdatePayload());
    }

    @Test
    public void authenticatedLegacyQuestionRouteConvertsServiceError() {
        IllegalStateException failure = new IllegalStateException("Scoped questions unavailable");
        RecordingScopedExamManagementService service = new RecordingScopedExamManagementService();
        service.setFailure(failure);
        Server server = new Server(
                0,
                service,
                new AuthService(new InMemoryUserRepository())
        );

        Response response = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_ALL_QUESTIONS, null),
                7001
        );

        assertError(response, "Scoped questions unavailable");
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

    private static Server questionBankServer(TestQuestionRepository questions,
                                             TestCourseRepository courses,
                                             User... users) {
        InMemoryUserRepository userRepository = new InMemoryUserRepository(users);
        return new Server(
                0,
                new ExamManagementService(questions, courses, userRepository),
                new AuthService(userRepository)
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

    private static User user(int userId, UserRole role, UserStatus status) {
        return new User(
                userId,
                "Development " + role,
                "user" + userId + "@hsts.local",
                PASSWORD_HASH,
                role,
                status
        );
    }

    private static CreateQuestionPayload createPayload(int courseId) {
        return new CreateQuestionPayload(
                courseId,
                "Created question",
                "Algebra",
                DifficultyLevel.MEDIUM,
                "",
                "One",
                "Two",
                "Three",
                "Four",
                2
        );
    }

    private static QuestionDTO normalizedQuestion(int questionId, int courseId, String content) {
        return new QuestionDTO(
                questionId,
                content,
                "Algebra",
                "MULTIPLE_CHOICE",
                "MEDIUM",
                "ACTIVE",
                "",
                "One",
                "Two",
                "Three",
                "Four",
                2,
                courseId,
                3,
                1
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

    private static final class TestCourseRepository extends CourseRepository {
        private final Map<Integer, List<CourseSummaryDTO>> assignments = new LinkedHashMap<>();
        private RuntimeException findFailure;

        @Override
        public List<CourseSummaryDTO> findAssignedToTeacher(int userId) {
            if (findFailure != null) {
                throw findFailure;
            }
            return assignments.getOrDefault(userId, List.of());
        }

        @Override
        public boolean isAssignedToTeacher(int userId, int courseId) {
            return assignments.getOrDefault(userId, List.of()).stream()
                    .anyMatch(course -> course.getCourseId() == courseId);
        }

        private void assign(int userId, CourseSummaryDTO course) {
            assignments.put(userId, List.of(course));
        }

        private void setFindFailure(RuntimeException findFailure) {
            this.findFailure = findFailure;
        }
    }

    private static final class TestQuestionRepository extends QuestionRepository {
        private List<QuestionDTO> questions = List.of();
        private final Map<Integer, QuestionDTO> createdQuestions = new LinkedHashMap<>();
        private int lastListUserId;
        private QuestionFilterPayload lastFilter;
        private int lastCreateUserId;

        @Override
        public List<QuestionDTO> findCurrentForTeacher(int authenticatedUserId,
                                                       QuestionFilterPayload filter) {
            lastListUserId = authenticatedUserId;
            lastFilter = filter;
            return questions;
        }

        @Override
        public int create(int createdByUserId, CreateQuestionPayload payload) {
            lastCreateUserId = createdByUserId;
            int questionId = 501;
            createdQuestions.put(
                    questionId,
                    normalizedQuestion(questionId, payload.getCourseId(), payload.getContent())
            );
            return questionId;
        }

        @Override
        public Optional<QuestionDTO> findCurrentByIdForTeacher(int authenticatedUserId,
                                                                int questionId) {
            return Optional.ofNullable(createdQuestions.get(questionId));
        }

        private void setQuestions(List<QuestionDTO> questions) {
            this.questions = questions;
        }

        private int getLastListUserId() {
            return lastListUserId;
        }

        private QuestionFilterPayload getLastFilter() {
            return lastFilter;
        }

        private int getLastCreateUserId() {
            return lastCreateUserId;
        }
    }

    private static final class RecordingScopedExamManagementService
            extends ExamManagementService {
        private List<QuestionDTO> listedQuestions = List.of();
        private QuestionDTO selectedQuestion;
        private QuestionDTO updatedQuestion;
        private RuntimeException failure;
        private int lastListUserId;
        private QuestionFilterPayload lastListFilter;
        private int lastGetUserId;
        private int lastQuestionId;
        private int lastUpdateUserId;
        private UpdateQuestionPayload lastUpdatePayload;

        private RecordingScopedExamManagementService() {
            super(new InMemoryQuestionRepository());
        }

        @Override
        public List<QuestionDTO> getQuestions(int authenticatedUserId,
                                              QuestionFilterPayload filter) {
            throwIfConfigured();
            lastListUserId = authenticatedUserId;
            lastListFilter = filter;
            return listedQuestions;
        }

        @Override
        public QuestionDTO getQuestionById(int authenticatedUserId, int questionId) {
            throwIfConfigured();
            lastGetUserId = authenticatedUserId;
            lastQuestionId = questionId;
            return selectedQuestion;
        }

        @Override
        public QuestionDTO updateQuestion(int authenticatedUserId,
                                          UpdateQuestionPayload payload) {
            throwIfConfigured();
            lastUpdateUserId = authenticatedUserId;
            lastUpdatePayload = payload;
            return updatedQuestion;
        }

        private void throwIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }

        private void setListedQuestions(List<QuestionDTO> listedQuestions) {
            this.listedQuestions = listedQuestions;
        }

        private void setSelectedQuestion(QuestionDTO selectedQuestion) {
            this.selectedQuestion = selectedQuestion;
        }

        private void setUpdatedQuestion(QuestionDTO updatedQuestion) {
            this.updatedQuestion = updatedQuestion;
        }

        private void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        private int getLastListUserId() {
            return lastListUserId;
        }

        private QuestionFilterPayload getLastListFilter() {
            return lastListFilter;
        }

        private int getLastGetUserId() {
            return lastGetUserId;
        }

        private int getLastQuestionId() {
            return lastQuestionId;
        }

        private int getLastUpdateUserId() {
            return lastUpdateUserId;
        }

        private UpdateQuestionPayload getLastUpdatePayload() {
            return lastUpdatePayload;
        }
    }
}
