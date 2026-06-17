package hsts.server.net;

import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.UpdateQuestionPayload;
import hsts.server.control.QuestionService;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final QuestionService questionService;

    public ClientHandler(Socket socket, QuestionService questionService) {
        this.socket = socket;
        this.questionService = questionService;
    }

    @Override
    public void run() {
        try (Socket ignored = socket;
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {

            output.flush();

            while (true) {
                Object object = input.readObject();
                if (!(object instanceof Request request)) {
                    output.writeObject(Response.error("Unsupported message type"));
                    output.flush();
                    continue;
                }

                Response response = handleRequest(request);
                output.writeObject(response);
                output.flush();
            }

        } catch (EOFException e) {
            System.out.println("Client disconnected");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Client connection error: " + e.getMessage());
        }
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
                            questionService.updateQuestion(payload.getQuestionId(), payload.getContent())
                    );
                }
            };
        } catch (Exception e) {
            return Response.error(e.getMessage());
        }
    }
}
