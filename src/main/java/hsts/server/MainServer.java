package hsts.server;

import hsts.external.ExternalBotSystem;
import hsts.server.bot.CourseBotProviderFactory;
import hsts.server.bot.source.BotSourceExtractor;
import hsts.server.control.AuthService;
import hsts.server.control.CourseBotService;
import hsts.server.control.ExamExecutionService;
import hsts.server.control.ExamManagementService;
import hsts.server.control.GradingService;
import hsts.server.control.ReportService;
import hsts.server.net.Server;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.CourseBotRepository;
import hsts.server.repository.BotConversationRepository;
import hsts.server.repository.DatabaseInitializer;
import hsts.server.repository.ExamExecutionRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.ExamSubmissionRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.ReportRepository;
import hsts.server.repository.StudentEnrollmentRepository;
import hsts.server.repository.StudentProfileRepository;
import hsts.server.repository.UserRepository;

import java.time.Clock;
import java.util.UUID;

public class MainServer {
    private static final int PORT = 5555;

    public static void main(String[] args) {
        new DatabaseInitializer().initialize();

        int port = PORT;
        QuestionRepository questionRepository = new QuestionRepository();
        CourseRepository courseRepository = new CourseRepository();
        UserRepository userRepository = new UserRepository();
        ExamRepository examRepository = new ExamRepository();
        ExamExecutionRepository examExecutionRepository =
                new ExamExecutionRepository();
        ExamSubmissionRepository examSubmissionRepository =
                new ExamSubmissionRepository();
        StudentEnrollmentRepository studentEnrollmentRepository =
                new StudentEnrollmentRepository();
        StudentProfileRepository studentProfileRepository =
                new StudentProfileRepository();
        ReportRepository reportRepository = new ReportRepository();
        CourseBotRepository courseBotRepository = new CourseBotRepository();
        BotConversationRepository botConversationRepository =
                new BotConversationRepository();
        BotSourceExtractor botSourceExtractor = new BotSourceExtractor();
        ExternalBotSystem externalBotSystem =
                CourseBotProviderFactory.createFromEnvironment();
        ExamManagementService examManagementService = new ExamManagementService(
                questionRepository,
                courseRepository,
                userRepository,
                examRepository
        );
        AuthService authService = new AuthService(userRepository);
        GradingService gradingService = new GradingService();
        ExamExecutionService examExecutionService = new ExamExecutionService(
                examExecutionRepository,
                examSubmissionRepository,
                studentEnrollmentRepository,
                studentProfileRepository,
                userRepository,
                examRepository,
                gradingService,
                Clock.systemDefaultZone()
        );
        ReportService reportService = new ReportService(
                reportRepository,
                userRepository,
                courseRepository,
                Clock.systemDefaultZone()
        );
        CourseBotService courseBotService = new CourseBotService(
                courseBotRepository,
                botConversationRepository,
                questionRepository,
                courseRepository,
                userRepository,
                examSubmissionRepository,
                botSourceExtractor,
                externalBotSystem,
                Clock.systemDefaultZone(),
                () -> UUID.randomUUID().toString()
        );

        Server server = new Server(
                port,
                examManagementService,
                authService,
                examExecutionService,
                reportService,
                courseBotService
        );
        server.startServer();
    }
}
