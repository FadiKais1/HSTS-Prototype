package hsts.server;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MainServerWiringTest {
    @Test
    public void mainServerConstructsOneExamRepositoryAndInjectsCompleteServiceDependencies()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/hsts/server/MainServer.java"
        ));
        String normalized = source.replaceAll("\\s+", " ");

        assertTrue(normalized.contains(
                "ExamRepository examRepository = new ExamRepository();"
        ));
        assertTrue(normalized.contains(
                "new ExamManagementService( questionRepository, courseRepository, "
                        + "userRepository, examRepository )"
        ));
        assertEquals(1, occurrences(source, "new UserRepository()"));
        assertEquals(1, occurrences(source, "new ExamRepository()"));
        assertEquals(1, occurrences(source, "new ExamExecutionRepository()"));
        assertEquals(1, occurrences(source, "new ExamSubmissionRepository()"));
        assertEquals(1, occurrences(source, "new StudentEnrollmentRepository()"));
        assertEquals(1, occurrences(source, "new StudentProfileRepository()"));
        assertEquals(1, occurrences(source, "new GradingService()"));
        assertEquals(1, occurrences(source, "new ReportRepository()"));
        assertEquals(1, occurrences(source, "new ReportService("));
        assertEquals(1, occurrences(source, "new ExamExecutionService("));
        assertEquals(1, occurrences(source, "new CourseBotRepository()"));
        assertEquals(1, occurrences(source, "new BotConversationRepository()"));
        assertEquals(1, occurrences(source, "new BotSourceExtractor()"));
        assertEquals(1, occurrences(source, "CourseBotProviderFactory.createFromEnvironment()"));
        assertEquals(0, occurrences(source, "new DeterministicExternalBotSystem()"));
        assertEquals(1, occurrences(source, "new CourseBotService("));
        assertTrue(normalized.contains(
                "new ExamExecutionService( examExecutionRepository, "
                        + "examSubmissionRepository, studentEnrollmentRepository, "
                        + "studentProfileRepository, userRepository, examRepository, "
                        + "gradingService, Clock.systemDefaultZone() )"
        ));
        assertTrue(!source.contains("Clock.systemUTC()"));
        assertTrue(normalized.contains(
                "new ReportService( reportRepository, userRepository, "
                        + "courseRepository, Clock.systemDefaultZone() )"
        ));
        assertTrue(normalized.contains(
                "new CourseBotService( courseBotRepository, botConversationRepository, "
                        + "questionRepository, courseRepository, userRepository, "
                        + "examSubmissionRepository, botSourceExtractor, externalBotSystem, "
                        + "Clock.systemDefaultZone(), () -> UUID.randomUUID().toString() )"
        ));
        assertTrue(normalized.contains(
                "new Server( port, examManagementService, authService, "
                        + "examExecutionService, reportService, courseBotService )"
        ));
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
