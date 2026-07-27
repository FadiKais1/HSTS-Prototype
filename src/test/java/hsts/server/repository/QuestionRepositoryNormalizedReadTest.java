package hsts.server.repository;

import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestionRepositoryNormalizedReadTest {
    @Test
    public void nullFilterUsesTeacherScopedNormalizedJoinsAndMapsCompleteDtosInIdOrder() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(List.of(
                questionRow(2, 11, 4, 3, "First question"),
                questionRow(9, 12, 5, 7, "Second question")
        ));
        QuestionRepository repository = new QuestionRepository(databaseController);

        List<QuestionDTO> result = repository.findCurrentForTeacher(1002, null);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getQuestionId());
        assertEquals(9, result.get(1).getQuestionId());
        assertCompleteQuestion(result.get(0));

        String sql = normalizeSql(databaseController.getSql());
        assertContainsNormalized(sql,
                "JOIN question_versions qv ON qv.question_id = q.question_id "
                        + "AND qv.version_no = q.current_version_no",
                "JOIN courses c ON c.course_id = q.course_id",
                "JOIN subjects s ON s.subject_id = c.subject_id",
                "JOIN teacher_courses tc ON tc.course_id = q.course_id "
                        + "AND tc.teacher_user_id = ?",
                "JOIN answer_options option_1 ON option_1.question_id = qv.question_id "
                        + "AND option_1.version_no = qv.version_no AND option_1.option_number = 1",
                "JOIN answer_options option_2 ON option_2.question_id = qv.question_id "
                        + "AND option_2.version_no = qv.version_no AND option_2.option_number = 2",
                "JOIN answer_options option_3 ON option_3.question_id = qv.question_id "
                        + "AND option_3.version_no = qv.version_no AND option_3.option_number = 3",
                "JOIN answer_options option_4 ON option_4.question_id = qv.question_id "
                        + "AND option_4.version_no = qv.version_no AND option_4.option_number = 4",
                "qv.question_type AS type",
                "q.status",
                "ORDER BY q.question_id"
        );
        assertEquals(1, databaseController.getParameters().size());
        assertEquals(1002, databaseController.getParameter(1));
    }

    @Test
    public void everyFilterUsesFixedSqlAndDeterministicParameterOrder() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of());
        QuestionRepository repository = new QuestionRepository(databaseController);
        QuestionFilterPayload filter = new QuestionFilterPayload(
                21,
                6,
                "  CaLcUlUs  ",
                DifficultyLevel.HARD,
                QuestionStatus.INACTIVE
        );

        repository.findCurrentForTeacher(1003, filter);

        String sql = normalizeSql(databaseController.getSql());
        assertContainsNormalized(sql,
                "WHERE 1 = 1",
                "AND q.course_id = ?",
                "AND c.subject_id = ?",
                "AND LOWER(qv.topic) LIKE ?",
                "AND qv.difficulty = ?",
                "AND q.status = ?",
                "ORDER BY q.question_id"
        );
        assertFalse(sql.contains("calculus"));
        assertEquals(6, databaseController.getParameters().size());
        assertEquals(1003, databaseController.getParameter(1));
        assertEquals(21, databaseController.getParameter(2));
        assertEquals(6, databaseController.getParameter(3));
        assertEquals("%calculus%", databaseController.getParameter(4));
        assertEquals("HARD", databaseController.getParameter(5));
        assertEquals("INACTIVE", databaseController.getParameter(6));
    }

    @Test
    public void blankTopicIsOmittedFromSqlAndParameters() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of());
        QuestionRepository repository = new QuestionRepository(databaseController);
        QuestionFilterPayload filter = new QuestionFilterPayload(
                null,
                null,
                "  \t  ",
                null,
                null
        );

        repository.findCurrentForTeacher(1002, filter);

        assertFalse(normalizeSql(databaseController.getSql()).contains("LOWER(qv.topic) LIKE ?"));
        assertEquals(1, databaseController.getParameters().size());
        assertEquals(1002, databaseController.getParameter(1));
    }

    @Test
    public void findCurrentByIdRequiresTeacherAndQuestionParametersAndMapsResult() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(
                List.of(questionRow(14, 31, 8, 2, "Assigned question"))
        );
        QuestionRepository repository = new QuestionRepository(databaseController);

        Optional<QuestionDTO> result = repository.findCurrentByIdForTeacher(1003, 14);

        assertTrue(result.isPresent());
        assertEquals(14, result.get().getQuestionId());
        assertEquals(31, result.get().getCourseId());
        assertEquals(8, result.get().getSubjectId());
        assertEquals(2, result.get().getVersionNo());
        assertContainsNormalized(normalizeSql(databaseController.getSql()),
                "AND tc.teacher_user_id = ?",
                "WHERE q.question_id = ?"
        );
        assertEquals(1003, databaseController.getParameter(1));
        assertEquals(14, databaseController.getParameter(2));
    }

    @Test
    public void findCurrentByIdReturnsEmptyForMissingOrUnauthorizedQuestion() {
        for (int questionId : new int[]{404, 405}) {
            RecordingDatabaseController databaseController =
                    new RecordingDatabaseController(List.of());
            QuestionRepository repository = new QuestionRepository(databaseController);

            assertFalse(repository.findCurrentByIdForTeacher(1002, questionId).isPresent());
            assertEquals(1002, databaseController.getParameter(1));
            assertEquals(questionId, databaseController.getParameter(2));
        }
    }

    @Test
    public void listSqlFailureIsWrappedWithOriginalCause() {
        SQLException sqlException = new SQLException("normalized list failed");
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of());
        databaseController.setQueryFailure(sqlException);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findCurrentForTeacher(1002, null)
        );

        assertEquals("Failed to load assigned questions", exception.getMessage());
        assertSame(sqlException, exception.getCause());
    }

    @Test
    public void idSqlFailureIsWrappedWithOriginalCause() {
        SQLException sqlException = new SQLException("normalized id failed");
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of());
        databaseController.setQueryFailure(sqlException);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findCurrentByIdForTeacher(1002, 1)
        );

        assertEquals("Failed to load assigned question by id", exception.getMessage());
        assertSame(sqlException, exception.getCause());
    }

    private static Map<String, Object> questionRow(int questionId, int courseId,
                                                    int subjectId, int versionNo,
                                                    String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question_id", questionId);
        row.put("course_id", courseId);
        row.put("subject_id", subjectId);
        row.put("version_no", versionNo);
        row.put("content", content);
        row.put("topic", "Calculus");
        row.put("type", "MULTIPLE_CHOICE");
        row.put("difficulty", "HARD");
        row.put("status", "INACTIVE");
        row.put("illustration_path", "images/calculus.png");
        row.put("answer_option_1", "One");
        row.put("answer_option_2", "Two");
        row.put("answer_option_3", "Three");
        row.put("answer_option_4", "Four");
        row.put("correct_option_number", 3);
        return row;
    }

    private static void assertCompleteQuestion(QuestionDTO question) {
        assertEquals(2, question.getQuestionId());
        assertEquals(11, question.getCourseId());
        assertEquals(4, question.getSubjectId());
        assertEquals(3, question.getVersionNo());
        assertEquals("First question", question.getContent());
        assertEquals("Calculus", question.getTopic());
        assertEquals("MULTIPLE_CHOICE", question.getType());
        assertEquals("HARD", question.getDifficulty());
        assertEquals("INACTIVE", question.getStatus());
        assertEquals("images/calculus.png", question.getIllustrationPath());
        assertEquals("One", question.getAnswerOption1());
        assertEquals("Two", question.getAnswerOption2());
        assertEquals("Three", question.getAnswerOption3());
        assertEquals("Four", question.getAnswerOption4());
        assertEquals(3, question.getCorrectOptionNumber());
    }

    private static void assertContainsNormalized(String normalizedSql, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(
                    "Missing SQL fragment: " + fragment,
                    normalizedSql.contains(normalizeSql(fragment))
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
                        if ("setInt".equals(method.getName())
                                || "setString".equals(method.getName())) {
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

        private Map<Integer, Object> getParameters() {
            return parameters;
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
