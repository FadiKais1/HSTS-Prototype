package hsts.server.net;

import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.UpdateQuestionPayload;
import hsts.ocsf.AbstractServer;
import hsts.ocsf.ConnectionToClient;
import hsts.server.control.QuestionService;

import java.io.IOException;

public class HSTSServer extends AbstractServer {
    private final QuestionService questionService;

    public HSTSServer(int port, QuestionService questionService) {
        super(port);
        this.questionService = questionService;
    }

    public void start() {
        try {
            listen();
        } catch (IOException e) {
            throw new IllegalStateException("Server failed", e);
        }
    }

    public void stop() {
        close();
    }

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

    private Response handleRequest(Request request) {
        try {
            RequestType type = request.getType();

            return switch (type) {
                case GET_ALL_QUESTIONS -> Response.success(
                        "Questions loaded successfully",
                        questionService.getAllQuestions()
                );

                case GET_QUESTION_BY_ID -> {
                    int questionId = (Integer) request.getPayload();
                    yield Response.success(
                            "Question loaded successfully",
                            questionService.getQuestionById(questionId)
                    );
                }

                case UPDATE_QUESTION -> {
                    UpdateQuestionPayload payload = (UpdateQuestionPayload) request.getPayload();
                    yield Response.success(
                            "Question updated successfully",
                            questionService.updateQuestion(payload)
                    );
                }
            };

        } catch (Exception e) {
            return Response.error(e.getMessage());
        }
    }

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