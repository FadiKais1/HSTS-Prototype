package hsts.server;

import hsts.external.ExternalBotSystem;
import hsts.server.bot.CourseBotProviderFactory;
import hsts.server.bot.source.BotSourceExtractor;
import hsts.server.control.AuthService;
import hsts.server.control.CourseBotService;
import hsts.server.control.ExamExecutionService;
import hsts.server.control.ExamManagementService;
import hsts.server.control.GradingService;
import hsts.server.control.PrincipalOversightService;
import hsts.server.control.ReportService;
import hsts.server.control.NotificationService;
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
import hsts.server.repository.NotificationRepository;

import java.time.Clock;
import java.util.UUID;

public class MainServer {
    private static final int PORT = 5555;

    public static void main(String[] args) {
        int port = resolvePort();
        new DatabaseInitializer().initialize();

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
        NotificationRepository notificationRepository = new NotificationRepository();
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
        NotificationService notificationService = new NotificationService(
                notificationRepository,
                userRepository,
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
        PrincipalOversightService principalOversightService =
                new PrincipalOversightService(
                        userRepository,
                        questionRepository,
                        examRepository,
                        examExecutionRepository,
                        examSubmissionRepository
                );

        Server server = new Server(
                port,
                examManagementService,
                authService,
                examExecutionService,
                reportService,
                courseBotService,
                principalOversightService,
                notificationService
        );
        server.startServer();
    }

    private static int resolvePort() {
        return resolvePort(
                System.getProperty("hsts.server.port"),
                System.getenv("HSTS_SERVER_PORT")
        );
    }

    static int resolvePort(String propertyValue, String environmentValue) {
        String configuredPort = propertyValue != null
                ? propertyValue.trim()
                : environmentValue != null
                ? environmentValue.trim()
                : String.valueOf(PORT);
        final int port;
        try {
            port = Integer.parseInt(configuredPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Server port must be a number from 1 to 65535", exception
            );
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "Server port must be between 1 and 65535"
            );
        }
        return port;
    }
}
