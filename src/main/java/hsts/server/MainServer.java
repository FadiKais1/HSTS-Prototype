package hsts.server;

import hsts.server.control.ExamManagementService;
import hsts.server.net.Server;
import hsts.server.repository.DatabaseInitializer;
import hsts.server.repository.QuestionRepository;

public class MainServer {
    private static final int PORT = 5555;

    public static void main(String[] args) {
        new DatabaseInitializer().initialize();

        int port = PORT;
        QuestionRepository questionRepository = new QuestionRepository();
        ExamManagementService examManagementService =
                new ExamManagementService(questionRepository);

        Server server = new Server(port, examManagementService);
        server.startServer();
    }
}
