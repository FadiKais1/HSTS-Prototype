package hsts.server.net;

import hsts.server.control.QuestionService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HSTSServer {
    private final int port;
    private final QuestionService questionService;
    private volatile boolean running;

    public HSTSServer(int port, QuestionService questionService) {
        this.port = port;
        this.questionService = questionService;
    }

    public void start() {
        running = true;
        System.out.println("HSTS server started on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, questionService);
                Thread clientThread = new Thread(handler, "hsts-client-handler");
                clientThread.start();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Server failed", e);
        }
    }

    public void stop() {
        running = false;
    }
}
