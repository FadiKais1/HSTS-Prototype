package hsts.server;

import hsts.server.control.AuthService;
import hsts.server.control.ExamExecutionService;
import hsts.server.control.ExamManagementService;
import hsts.server.control.GradingService;
import hsts.server.control.ReportService;
import hsts.server.net.Server;
import hsts.server.repository.CourseRepository;
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

        Server server = new Server(
                port,
                examManagementService,
                authService,
                examExecutionService,
                reportService
        );
        server.startServer();
    }
}
