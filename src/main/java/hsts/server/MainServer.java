package hsts.server;

import hsts.server.control.QuestionService;
import hsts.server.net.HSTSServer;
import hsts.server.repository.DatabaseInitializer;
import hsts.server.repository.QuestionRepository;

public class MainServer {
    private static final int PORT = 5555;

    public static void main(String[] args) {
        new DatabaseInitializer().initialize();

        QuestionRepository questionRepository = new QuestionRepository();
        QuestionService questionService = new QuestionService(questionRepository);

        HSTSServer server = new HSTSServer(PORT, questionService);
        server.start();
    }
}
