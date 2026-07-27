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
