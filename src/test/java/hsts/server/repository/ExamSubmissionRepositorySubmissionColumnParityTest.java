package hsts.server.repository;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * Several queries build the same submission record, and the mapper reads a fixed
 * set of columns from whichever one ran. Adding a column to the mapper but to
 * only some of the queries leaves the rest failing at runtime with a
 * missing-column error, which is invisible to every test that uses a fake
 * database.
 *
 * <p>That is exactly how resuming an exam broke: the mapper began reading
 * extension_reason while the query behind resume still selected only up to
 * extra_minutes, so a student who left an attempt could not return to it.</p>
 */
public class ExamSubmissionRepositorySubmissionColumnParityTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/hsts/server/repository/ExamSubmissionRepository.java"
    );

    /**
     * Every select that reads a submission's extra_minutes must also read
     * extension_reason, because the record carrying one carries the other.
     */
    @Test
    public void everySubmissionSelectCarriesTheColumnsTheRecordNeeds() throws Exception {
        String source = Files.readString(SOURCE);
        String[] lines = source.split("\n", -1);

        List<Integer> offenders = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();

            // Only plain column selections, not arithmetic such as
            // "allocated_duration_minutes + extra_minutes" used for a deadline.
            if (!line.equals("submission.extra_minutes,")
                    && !line.equals("submission.extra_minutes")) {
                continue;
            }
            String next = index + 1 < lines.length ? lines[index + 1].trim() : "";
            if (!next.startsWith("submission.extension_reason")) {
                offenders.add(index + 1);
            }
        }

        assertTrue(
                "These submission selects read extra_minutes without extension_reason, "
                        + "so the record mapper will fail at runtime. Lines: " + offenders,
                offenders.isEmpty()
        );
    }

    /** The mapper is the reason the parity above matters; keep them together. */
    @Test
    public void theRecordMapperStillReadsBothColumns() throws Exception {
        String source = Files.readString(SOURCE);
        Matcher matcher = Pattern.compile(
                "resultSet\\.getInt\\(\"extra_minutes\"\\),\\s*"
                        + "resultSet\\.getString\\(\"extension_reason\"\\)"
        ).matcher(source);

        assertTrue(
                "The submission record mapper should read extra_minutes and "
                        + "extension_reason together; update this guard if that changes.",
                matcher.find()
        );
    }
}
