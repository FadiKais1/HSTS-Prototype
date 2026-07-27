package hsts.server.net;

import hsts.common.CreateExamPayload;
import hsts.common.CreateQuestionPayload;
import hsts.common.LoginRequestPayload;
import hsts.common.LoginResult;
import hsts.common.QuestionFilterPayload;
import hsts.common.QuestionIdPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.UpdateQuestionPayload;
import hsts.ocsf.AbstractServer;
import hsts.ocsf.ConnectionToClient;
import hsts.server.control.AuthService;
import hsts.server.control.CourseBotService;
import hsts.server.control.ExamExecutionService;
import hsts.server.control.ExamManagementService;
import hsts.server.control.GradingService;
import hsts.server.control.NotificationService;
import hsts.server.control.ReportService;

import java.io.IOException;

public class Server extends AbstractServer {
    private static final String AUTHENTICATED_USER_ID = "hsts.auth.userId";
    private static final String AUTHENTICATED_SESSION_ID = "hsts.auth.sessionId";

    private int port;
    private boolean running;

    // RELATIONSHIP-DERIVED: Server dispatches requests to the Control layer.
    private ExamManagementService examManagementService;
    private ExamExecutionService examExecutionService;
    private AuthService authService;
    private GradingService gradingService;
    private ReportService reportService;
    private NotificationService notificationService;
    private CourseBotService courseBotService;

    public Server(int port, ExamManagementService examManagementService, AuthService authService) {
        super(port);
        this.port = port;
        this.examManagementService = examManagementService;
        this.authService = authService;
    }

    public void startServer() {
        try {
            listen();
        } catch (IOException e) {
            throw new IllegalStateException("Server failed", e);
        }
    }

    public void stopServer() {
        close();
    }

    public Response receiveRequest(Request request) {
        return handleRequest(request);
    }

    public Response handleRequest(Request request) {
        try {
            RequestType type = request.getType();

            return switch (type) {
                case GET_ALL_QUESTIONS -> Response.success(
                        "Questions loaded successfully",
                        examManagementService.getAllQuestions()
                );

                case GET_QUESTION_BY_ID -> {
                    int questionId = (Integer) request.getPayload();
                    yield Response.success(
                            "Question loaded successfully",
                            examManagementService.getQuestionById(questionId)
                    );
                }

                case UPDATE_QUESTION -> {
                    UpdateQuestionPayload payload = (UpdateQuestionPayload) request.getPayload();
                    yield Response.success(
                            "Question updated successfully",
                            examManagementService.updateQuestion(payload)
                    );
                }

                case LOGIN -> {
                    if (!(request.getPayload() instanceof LoginRequestPayload payload)) {
                        throw new IllegalArgumentException("Login request data is required");
                    }
                    yield Response.success(
                            "Login successful",
                            authService.login(payload)
                    );
                }

                case LOGOUT -> Response.error("Connection context required");

                case GET_MY_COURSES, LIST_QUESTIONS, CREATE_QUESTION,
                     ACTIVATE_QUESTION, DEACTIVATE_QUESTION, GET_QUESTION_HISTORY,
                     LIST_MY_EXAMS, GET_MY_EXAM, CREATE_EXAM,
                     LIST_PENDING_EXAMS, GET_PENDING_EXAM ->
                        Response.error("Authentication context required");
            };

        } catch (Exception e) {
            return Response.error(e.getMessage());
        }
    }

    Response handleAuthenticatedRequest(Request request, int authenticatedUserId) {
        try {
            return switch (request.getType()) {
                case GET_ALL_QUESTIONS -> Response.success(
                        "Questions loaded successfully",
                        examManagementService.getQuestions(authenticatedUserId, null)
                );

                case GET_QUESTION_BY_ID -> {
                    int questionId = (Integer) request.getPayload();
                    yield Response.success(
                            "Question loaded successfully",
                            examManagementService.getQuestionById(
                                    authenticatedUserId,
                                    questionId
                            )
                    );
                }

                case UPDATE_QUESTION -> {
                    UpdateQuestionPayload payload = (UpdateQuestionPayload) request.getPayload();
                    yield Response.success(
                            "Question updated successfully",
                            examManagementService.updateQuestion(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case GET_MY_COURSES -> Response.success(
                        "Courses loaded successfully",
                        examManagementService.getCoursesForTeacher(authenticatedUserId)
                );

                case LIST_QUESTIONS -> {
                    Object requestPayload = request.getPayload();
                    if (requestPayload != null
                            && !(requestPayload instanceof QuestionFilterPayload)) {
                        throw new IllegalArgumentException("Question filter data is invalid");
                    }
                    yield Response.success(
                            "Questions loaded successfully",
                            examManagementService.getQuestions(
                                    authenticatedUserId,
                                    (QuestionFilterPayload) requestPayload
                            )
                    );
                }

                case CREATE_QUESTION -> {
                    if (!(request.getPayload() instanceof CreateQuestionPayload payload)) {
                        throw new IllegalArgumentException("Question data is required");
                    }
                    yield Response.success(
                            "Question created successfully",
                            examManagementService.createQuestion(authenticatedUserId, payload)
                    );
                }

                case ACTIVATE_QUESTION -> {
                    int questionId = requireQuestionIdPayload(request).getQuestionId();
                    yield Response.success(
                            "Question activated successfully",
                            examManagementService.activateQuestion(
                                    authenticatedUserId,
                                    questionId
                            )
                    );
                }

                case DEACTIVATE_QUESTION -> {
                    int questionId = requireQuestionIdPayload(request).getQuestionId();
                    yield Response.success(
                            "Question deactivated successfully",
                            examManagementService.deactivateQuestion(
                                    authenticatedUserId,
                                    questionId
                            )
                    );
                }

                case GET_QUESTION_HISTORY -> {
                    int questionId = requireQuestionIdPayload(request).getQuestionId();
                    yield Response.success(
                            "Question history loaded successfully",
                            examManagementService.getQuestionHistory(
                                    authenticatedUserId,
                                    questionId
                            )
                    );
                }

                case LIST_MY_EXAMS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "Exams loaded successfully",
                            examManagementService.getMyExams(authenticatedUserId)
                    );
                }

                case GET_MY_EXAM -> {
                    int examId = requireExamIdPayload(request);
                    yield Response.success(
                            "Exam loaded successfully",
                            examManagementService.getExamForTeacher(
                                    authenticatedUserId,
                                    examId
                            )
                    );
                }

                case CREATE_EXAM -> {
                    if (!(request.getPayload() instanceof CreateExamPayload payload)) {
                        throw new IllegalArgumentException("Exam creation data is missing");
                    }
                    yield Response.success(
                            "Exam created successfully",
                            examManagementService.createExam(authenticatedUserId, payload)
                    );
                }

                case LIST_PENDING_EXAMS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "Pending exams loaded successfully",
                            examManagementService.getPendingExams(authenticatedUserId)
                    );
                }

                case GET_PENDING_EXAM -> {
                    int examId = requireExamIdPayload(request);
                    yield Response.success(
                            "Pending exam loaded successfully",
                            examManagementService.getExamForCoordinator(
                                    authenticatedUserId,
                                    examId
                            )
                    );
                }

                default -> handleRequest(request);
            };
        } catch (Exception exception) {
            return Response.error(exception.getMessage());
        }
    }

    public void sendResponse(Response response) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    // COMPATIBILITY-ONLY: OCSF supplies the client connection with each received message.
    @Override
    protected void handleMessageFromClient(Object message, ConnectionToClient client) {
        Response response;

        if (!(message instanceof Request request)) {
            response = Response.error("Unsupported message type");
            sendResponse(client, response);
            return;
        }

        if (request.getType() == RequestType.LOGIN) {
            if (isAuthenticated(client)) {
                response = Response.error("User is already logged in");
            } else {
                response = handleRequest(request);
                if (response.isSuccess() && response.getPayload() instanceof LoginResult loginResult) {
                    bindAuthentication(client, loginResult);
                }
            }
        } else if (!isAuthenticated(client)) {
            response = Response.error("Authentication required");
        } else if (request.getType() == RequestType.LOGOUT) {
            response = logout(client);
        } else {
            int authenticatedUserId = (Integer) client.getInfo(AUTHENTICATED_USER_ID);
            response = handleAuthenticatedRequest(request, authenticatedUserId);
        }
        sendResponse(client, response);
    }

    private void bindAuthentication(ConnectionToClient client, LoginResult loginResult) {
        client.setInfo(AUTHENTICATED_USER_ID, loginResult.getUserId());
        client.setInfo(AUTHENTICATED_SESSION_ID, loginResult.getSessionId());
    }

    private QuestionIdPayload requireQuestionIdPayload(Request request) {
        if (!(request.getPayload() instanceof QuestionIdPayload payload)) {
            throw new IllegalArgumentException("Question ID is required");
        }
        return payload;
    }

    private int requireExamIdPayload(Request request) {
        if (!(request.getPayload() instanceof Integer examId)) {
            throw new IllegalArgumentException("Exam ID is required");
        }
        return examId;
    }

    private void requireEmptyPayload(Request request) {
        if (request.getPayload() != null) {
            throw new IllegalArgumentException("Request payload must be empty");
        }
    }

    private boolean isAuthenticated(ConnectionToClient client) {
        Object userId = client.getInfo(AUTHENTICATED_USER_ID);
        Object sessionId = client.getInfo(AUTHENTICATED_SESSION_ID);

        if (userId instanceof Integer authenticatedUserId
                && sessionId instanceof String authenticatedSessionId) {
            if (authService.isSessionActive(authenticatedUserId, authenticatedSessionId)) {
                return true;
            }

            clearAuthentication(client);
        }

        return false;
    }

    private Response logout(ConnectionToClient client) {
        int userId = (Integer) client.getInfo(AUTHENTICATED_USER_ID);
        String sessionId = (String) client.getInfo(AUTHENTICATED_SESSION_ID);

        try {
            authService.logout(userId, sessionId);
            return Response.success("Logout successful", null);
        } catch (Exception exception) {
            return Response.error(exception.getMessage());
        } finally {
            clearAuthentication(client);
        }
    }

    private void clearAuthentication(ConnectionToClient client) {
        client.setInfo(AUTHENTICATED_USER_ID, null);
        client.setInfo(AUTHENTICATED_SESSION_ID, null);
    }

    private void cleanupAuthentication(ConnectionToClient client) {
        Object userId = client.getInfo(AUTHENTICATED_USER_ID);
        Object sessionId = client.getInfo(AUTHENTICATED_SESSION_ID);

        try {
            if (userId instanceof Integer authenticatedUserId
                    && sessionId instanceof String authenticatedSessionId) {
                authService.logout(authenticatedUserId, authenticatedSessionId);
            }
        } catch (Exception exception) {
            System.out.println("Failed to clean up client authentication");
        } finally {
            clearAuthentication(client);
        }
    }

    // COMPATIBILITY-ONLY: OCSF response delivery requires a target client connection.
    private void sendResponse(ConnectionToClient client, Response response) {
        try {
            client.sendToClient(response);
        } catch (IOException e) {
            System.out.println("Failed to send response to client: " + e.getMessage());
        }
    }

    @Override
    protected void serverStarted() {
        running = true;
        System.out.println("HSTS OCSF server started on port " + getPort());
    }

    @Override
    protected void serverStopped() {
        running = false;
    }

    @Override
    protected void clientConnected(ConnectionToClient client) {
        System.out.println("Client connected: " + client);
    }

    @Override
    protected void clientDisconnected(ConnectionToClient client) {
        cleanupAuthentication(client);
        System.out.println("Client disconnected: " + client);
    }

    @Override
    protected void listeningException(Exception exception) {
        System.out.println("Server listening error: " + exception.getMessage());
    }

    @Override
    protected void serverClosed() {
        running = false;
        System.out.println("HSTS OCSF server closed");
    }
}
