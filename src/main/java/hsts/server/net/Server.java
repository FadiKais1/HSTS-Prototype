package hsts.server.net;

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

    public Server(int port, ExamManagementService examManagementService) {
        super(port);
        this.port = port;
        this.examManagementService = examManagementService;
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
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
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
            };

        } catch (Exception e) {
            return Response.error(e.getMessage());
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

        response = handleRequest(request);
        sendResponse(client, response);
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
        System.out.println("HSTS OCSF server started on port " + getPort());
    }

    @Override
    protected void clientConnected(ConnectionToClient client) {
        System.out.println("Client connected: " + client);
    }

    @Override
    protected void clientDisconnected(ConnectionToClient client) {
        System.out.println("Client disconnected: " + client);
    }

    @Override
    protected void listeningException(Exception exception) {
        System.out.println("Server listening error: " + exception.getMessage());
    }

    @Override
    protected void serverClosed() {
        System.out.println("HSTS OCSF server closed");
    }
}
