package hsts.server.repository;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrincipalRepositoryReadArchitectureTest {
    @Test
    public void questionReadsUseExactVersionsOrderedOptionsAndNoAssignmentForgery()
            throws Exception {
        String source = read("QuestionRepository.java");
        String block = between(source, "PRINCIPAL_QUESTION_SELECT", "public QuestionRepository()");
        assertTrue(block.contains("JOIN question_versions qv ON qv.question_id = q.question_id"));
        assertTrue(block.contains("qv.version_no = q.current_version_no"));
        assertTrue(block.contains("q.question_id = ? AND qv.version_no = ?"));
        for (int option = 1; option <= 4; option++) {
            assertTrue(block.contains("option_" + option + ".option_number = " + option));
        }
        assertFalse(block.contains("teacher_courses"));
        assertReadOnly(block);
    }

    /**
     * The Principal execution list keeps its own SELECT because it must not carry
     * the TEACHER/COORDINATOR authorisation join that EXECUTION_SUMMARY_SELECT
     * ends with. That duplication once drifted: the column list lost
     * cumulative_extension_minutes while the shared mapper still read it, so the
     * Results tab failed with a missing-column error and looked empty.
     *
     * <p>This pins every column the mapper reads, so the two cannot diverge
     * silently again.</p>
     */
    @Test
    public void principalExecutionListSelectsEveryColumnTheSummaryMapperReads()
            throws Exception {
        String source = read("ExamExecutionRepository.java");
        String block = between(
                source,
                "PRINCIPAL_EXECUTION_LIST_SQL",
                "STUDENT_EXECUTION_PREVIEW_SQL"
        );

        for (String column : new String[]{
                "execution_id", "execution_code", "exam_id", "exam_version_no",
                "exam_code", "exam_title", "course_id", "course_name",
                "opening_time", "closing_time", "duration_minutes",
                "cumulative_extension_minutes", "status", "created_by_user_id",
                "creator_name", "created_at", "started_count",
                "submitted_count", "auto_submitted_count"
        }) {
            assertTrue(
                    "Principal execution list must select " + column
                            + " because mapSummary reads it",
                    block.contains(column)
            );
        }

        // The Principal sees every execution, so this query must not filter by
        // creator or require a teacher or coordinator role.
        assertFalse(block.contains("created_by_user_id = ?"));
        assertFalse(block.contains("'TEACHER'"));
        assertReadOnly(block);
    }

    @Test
    public void examReadsPreserveExactExamAndQuestionVersions() throws Exception {
        String source = read("ExamRepository.java");
        String block = between(source, "PRINCIPAL_EXAM_LIST_SQL", "EXAM_ENTITY_COLUMNS");
        assertTrue(block.contains("ev.version_no = ?"));
        assertTrue(source.contains("EXAM_QUESTION_SNAPSHOTS_SQL"));
        assertTrue(source.contains("evq.question_version_no"));
        assertReadOnly(block);
    }

    @Test
    public void globalExecutionAndResultReadsContainNoManagerAssignmentOrWrites()
            throws Exception {
        String execution = read("ExamExecutionRepository.java");
        String executionBlock = between(execution,
                "PRINCIPAL_EXECUTION_LIST_SQL", "STUDENT_EXECUTION_PREVIEW_SQL");
        assertTrue(executionBlock.contains("execution.exam_version_no"));
        assertFalse(executionBlock.contains("teacher_courses"));
        assertFalse(executionBlock.contains("FOR UPDATE"));
        assertReadOnly(executionBlock);

        String submission = read("ExamSubmissionRepository.java");
        String submissionBlock = between(submission,
                "PRINCIPAL_SUBMISSION_SUMMARIES_SQL", "SUBMISSION_ANSWER_REVIEW_SQL");
        assertTrue(submissionBlock.contains("student.full_name AS student_name"));
        assertTrue(submissionBlock.contains("execution.exam_version_no"));
        assertFalse(submissionBlock.contains("password_hash"));
        assertFalse(submissionBlock.contains("identity_number_hash"));
        assertReadOnly(submissionBlock);
    }

    private static void assertReadOnly(String source) {
        String upper = source.toUpperCase();
        assertFalse(upper.contains("INSERT INTO"));
        assertFalse(upper.contains("UPDATE "));
        assertFalse(upper.contains("DELETE FROM"));
        assertFalse(upper.contains("FOR UPDATE"));
    }

    private static String read(String file) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/hsts/server/repository", file
        ));
    }

    private static String between(String source, String start, String end) {
        int first = source.indexOf(start);
        int last = source.indexOf(end, first);
        assertTrue(first >= 0 && last > first);
        return source.substring(first, last);
    }
}
