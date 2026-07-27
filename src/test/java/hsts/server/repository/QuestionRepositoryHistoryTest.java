package hsts.server.repository;

import hsts.common.QuestionVersionDTO;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionType;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestionRepositoryHistoryTest {
    @Test
    public void assignedQuestionMapsCompleteVersionSnapshotsNewestFirst() {
        LocalDateTime newestCreatedAt = LocalDateTime.of(2026, 7, 27, 12, 0, 2);
        LocalDateTime oldestCreatedAt = LocalDateTime.of(2026, 7, 27, 12, 0, 1);
        RecordingDatabaseController databaseController = new RecordingDatabaseController(List.of(
                versionRow(
                        31, 2, 7, "New content", "New topic", "HARD",
                        "new.png", "New 1", "New 2", "New 3", "New 4",
                        4, 1003, newestCreatedAt
                ),
                versionRow(
                        31, 1, 7, "Old content", "Old topic", "EASY",
                        "old.png", "Old 1", "Old 2", "Old 3", "Old 4",
                        2, 1002, oldestCreatedAt
                )
        ));
        QuestionRepository repository = new QuestionRepository(databaseController);

        List<QuestionVersionDTO> versions = repository.findVersionsForTeacher(1003, 31);

        assertEquals(2, versions.size());
        assertVersion(
                versions.get(0),
                31, 2, 7, "New content", "New topic", DifficultyLevel.HARD,
                "new.png", "New 1", "New 2", "New 3", "New 4",
                4, 1003, newestCreatedAt
        );
        assertVersion(
                versions.get(1),
                31, 1, 7, "Old content", "Old topic", DifficultyLevel.EASY,
                "old.png", "Old 1", "Old 2", "Old 3", "Old 4",
                2, 1002, oldestCreatedAt
        );

        String sql = normalizeSql(databaseController.getSql());
        assertContainsNormalized(sql,
                "FROM question_versions qv",
                "JOIN questions q ON q.question_id = qv.question_id",
                "JOIN teacher_courses tc ON tc.course_id = q.course_id "
                        + "AND tc.teacher_user_id = ?",
                "JOIN answer_options option_1 ON option_1.question_id = qv.question_id "
                        + "AND option_1.version_no = qv.version_no "
                        + "AND option_1.option_number = 1",
                "JOIN answer_options option_2 ON option_2.question_id = qv.question_id "
                        + "AND option_2.version_no = qv.version_no "
                        + "AND option_2.option_number = 2",
                "JOIN answer_options option_3 ON option_3.question_id = qv.question_id "
                        + "AND option_3.version_no = qv.version_no "
                        + "AND option_3.option_number = 3",
                "JOIN answer_options option_4 ON option_4.question_id = qv.question_id "
                        + "AND option_4.version_no = qv.version_no "
                        + "AND option_4.option_number = 4",
                "WHERE qv.question_id = ?",
                "ORDER BY qv.version_no DESC"
        );
        assertFalse(sql.contains("q.answer_option_1"));
        assertFalse(sql.contains("q.answer_option_2"));
        assertFalse(sql.contains("q.answer_option_3"));
        assertFalse(sql.contains("q.answer_option_4"));
        assertEquals(1003, databaseController.getParameter(1));
        assertEquals(31, databaseController.getParameter(2));
    }

    @Test
    public void missingUnassignedOrVersionlessQuestionReturnsEmptyList() {
        for (int questionId : new int[]{404, 405, 406}) {
            RecordingDatabaseController databaseController =
                    new RecordingDatabaseController(List.of());
            QuestionRepository repository = new QuestionRepository(databaseController);

            List<QuestionVersionDTO> versions = repository.findVersionsForTeacher(1002, questionId);

            assertTrue(versions.isEmpty());
            assertEquals(1002, databaseController.getParameter(1));
            assertEquals(questionId, databaseController.getParameter(2));
        }
    }

    @Test
    public void sqlFailureUsesExactWrapperAndPreservesCause() {
        SQLException failure = new SQLException("history query failed");
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of());
        databaseController.setQueryFailure(failure);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findVersionsForTeacher(1002, 31)
        );

        assertEquals("Failed to load question versions", exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    private static Map<String, Object> versionRow(
            int questionId,
            int versionNo,
            int courseId,
            String content,
            String topic,
            String difficulty,
            String illustrationPath,
            String option1,
            String option2,
            String option3,
            String option4,
            int correctOptionNumber,
            int createdByUserId,
            LocalDateTime createdAt
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question_id", questionId);
        row.put("version_no", versionNo);
        row.put("course_id", courseId);
        row.put("content", content);
        row.put("topic", topic);
        row.put("question_type", "MULTIPLE_CHOICE");
        row.put("difficulty", difficulty);
        row.put("illustration_path", illustrationPath);
        row.put("answer_option_1", option1);
        row.put("answer_option_2", option2);
        row.put("answer_option_3", option3);
        row.put("answer_option_4", option4);
        row.put("correct_option_number", correctOptionNumber);
        row.put("created_by_user_id", createdByUserId);
        row.put("created_at", createdAt);
        return row;
    }

    private static void assertVersion(
            QuestionVersionDTO version,
            int questionId,
            int versionNo,
            int courseId,
            String content,
            String topic,
            DifficultyLevel difficulty,
            String illustrationPath,
            String option1,
            String option2,
            String option3,
            String option4,
            int correctOptionNumber,
            int createdByUserId,
            LocalDateTime createdAt
    ) {
        assertEquals(questionId, version.getQuestionId());
        assertEquals(versionNo, version.getVersionNo());
        assertEquals(courseId, version.getCourseId());
        assertEquals(content, version.getContent());
        assertEquals(topic, version.getTopic());
        assertEquals(QuestionType.MULTIPLE_CHOICE, version.getType());
        assertEquals(difficulty, version.getDifficulty());
        assertEquals(illustrationPath, version.getIllustrationPath());
        assertEquals(option1, version.getAnswerOption1());
        assertEquals(option2, version.getAnswerOption2());
        assertEquals(option3, version.getAnswerOption3());
        assertEquals(option4, version.getAnswerOption4());
        assertEquals(correctOptionNumber, version.getCorrectOptionNumber());
        assertEquals(createdByUserId, version.getCreatedByUserId());
        assertEquals(createdAt, version.getCreatedAt());
    }

    private static void assertContainsNormalized(String sql, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(
                    "Missing SQL fragment: " + fragment,
                    sql.contains(normalizeSql(fragment))
            );
        }
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static final class RecordingDatabaseController extends DatabaseController {
        private final List<Map<String, Object>> rows;
        private final Map<Integer, Object> parameters = new HashMap<>();
        private String sql;
        private SQLException queryFailure;

        private RecordingDatabaseController(List<Map<String, Object>> rows) {
            this.rows = new ArrayList<>(rows);
        }

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            sql = (String) arguments[0];
                            return preparedStatement();
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private PreparedStatement preparedStatement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        if ("setInt".equals(method.getName())) {
                            parameters.put((Integer) arguments[0], arguments[1]);
                            return null;
                        }
                        if ("executeQuery".equals(method.getName())) {
                            if (queryFailure != null) {
                                throw queryFailure;
                            }
                            return resultSet();
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private ResultSet resultSet() {
            int[] index = {-1};
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, arguments) -> {
                        if ("next".equals(method.getName())) {
                            index[0]++;
                            return index[0] < rows.size();
                        }
                        if ("getInt".equals(method.getName())) {
                            return (Integer) currentRow(index[0]).get((String) arguments[0]);
                        }
                        if ("getString".equals(method.getName())) {
                            return (String) currentRow(index[0]).get((String) arguments[0]);
                        }
                        if ("getObject".equals(method.getName())) {
                            Object value = currentRow(index[0]).get((String) arguments[0]);
                            assertEquals(LocalDateTime.class, arguments[1]);
                            return value;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private Map<String, Object> currentRow(int index) {
            return rows.get(index);
        }

        private String getSql() {
            return sql;
        }

        private Object getParameter(int index) {
            return parameters.get(index);
        }

        private void setQueryFailure(SQLException queryFailure) {
            this.queryFailure = queryFailure;
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0.0F;
            }
            if (returnType == double.class) {
                return 0.0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
