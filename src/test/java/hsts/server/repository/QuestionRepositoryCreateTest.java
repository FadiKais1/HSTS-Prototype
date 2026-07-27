package hsts.server.repository;

import hsts.common.type.DifficultyLevel;
import hsts.server.entity.Question;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestionRepositoryCreateTest {
    @Test
    public void createMapsLegacyAndNormalizedRowsAndCommitsAfterFourOptions() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setGeneratedQuestionId(42);
        QuestionRepository repository = new QuestionRepository(databaseController);
        Question questionEntity = question(DifficultyLevel.HARD);

        int questionId = repository.create(1002, 21, questionEntity);

        assertEquals(42, questionId);
        assertEquals(1, databaseController.getConnectionCalls());
        assertTrue(databaseController.isCommitted());
        assertFalse(databaseController.isRolledBack());
        assertTrue(databaseController.isAutoCommit());

        StatementRecord question = databaseController.getStatement("question");
        StatementRecord version = databaseController.getStatement("version");
        StatementRecord option = databaseController.getStatement("option");
        assertEquals(Statement.RETURN_GENERATED_KEYS, question.getGeneratedKeysFlag());
        assertEquals(1, question.getExecutions().size());
        assertEquals(1, version.getExecutions().size());
        assertEquals(4, option.getExecutions().size());

        Map<Integer, Object> questionParameters = question.getExecutions().get(0);
        assertEquals("New content", questionParameters.get(1));
        assertEquals("Calculus", questionParameters.get(2));
        assertEquals("MULTIPLE_CHOICE", questionParameters.get(3));
        assertEquals("HARD", questionParameters.get(4));
        assertEquals("ACTIVE", questionParameters.get(5));
        assertEquals("images/new.png", questionParameters.get(6));
        assertEquals("One", questionParameters.get(7));
        assertEquals("Two", questionParameters.get(8));
        assertEquals("Three", questionParameters.get(9));
        assertEquals("Four", questionParameters.get(10));
        assertEquals(3, questionParameters.get(11));
        assertEquals(21, questionParameters.get(12));
        assertEquals(1002, questionParameters.get(13));
        assertEquals(1, questionParameters.get(14));

        Map<Integer, Object> versionParameters = version.getExecutions().get(0);
        assertEquals(42, versionParameters.get(1));
        assertEquals(1, versionParameters.get(2));
        assertEquals("New content", versionParameters.get(3));
        assertEquals("Calculus", versionParameters.get(4));
        assertEquals("MULTIPLE_CHOICE", versionParameters.get(5));
        assertEquals("HARD", versionParameters.get(6));
        assertEquals("images/new.png", versionParameters.get(7));
        assertEquals(3, versionParameters.get(8));
        assertEquals(1002, versionParameters.get(9));

        Object createdAt = questionParameters.get(15);
        assertTrue(createdAt instanceof LocalDateTime);
        assertSame(createdAt, questionParameters.get(16));
        assertSame(createdAt, versionParameters.get(10));

        String[] expectedTexts = {"One", "Two", "Three", "Four"};
        for (int index = 0; index < 4; index++) {
            Map<Integer, Object> optionParameters = option.getExecutions().get(index);
            assertEquals(42, optionParameters.get(1));
            assertEquals(1, optionParameters.get(2));
            assertEquals(index + 1, optionParameters.get(3));
            assertEquals(expectedTexts[index], optionParameters.get(4));
        }

        assertEquals(List.of(
                "setAutoCommit:false",
                "execute:question",
                "execute:version",
                "execute:option",
                "execute:option",
                "execute:option",
                "execute:option",
                "commit",
                "setAutoCommit:true"
        ), databaseController.getEvents());

        String allSql = question.getSql() + version.getSql() + option.getSql();
        assertFalse(allSql.contains("INSERT IGNORE"));
        assertFalse(allSql.contains("ON DUPLICATE KEY UPDATE"));
        assertFalse(allSql.contains("teacher_courses"));
    }

    @Test
    public void createRestoresOriginallyDisabledAutoCommit() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(false);
        QuestionRepository repository = new QuestionRepository(databaseController);

        assertEquals(17, repository.create(1002, 21, question(DifficultyLevel.EASY)));
        assertFalse(databaseController.isAutoCommit());
        assertEquals("setAutoCommit:false", databaseController.getEvents().get(0));
        assertEquals("setAutoCommit:false", databaseController.getEvents().get(8));
    }

    @Test
    public void missingGeneratedKeyRollsBackAndRestoresAutoCommit() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setGeneratedKeyAvailable(false);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.create(1002, 21, question(DifficultyLevel.MEDIUM))
        );

        assertEquals("Failed to create question", exception.getMessage());
        assertTrue(exception.getCause() instanceof SQLException);
        assertTrue(databaseController.isRolledBack());
        assertFalse(databaseController.isCommitted());
        assertTrue(databaseController.isAutoCommit());
    }

    @Test
    public void failureAtQuestionVersionOrAnyOptionRollsBackAndRestoresAutoCommit() {
        for (int failingExecution = 1; failingExecution <= 6; failingExecution++) {
            SQLException failure = new SQLException("insert step failed");
            RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
            databaseController.failExecution(failingExecution, failure);
            QuestionRepository repository = new QuestionRepository(databaseController);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> repository.create(1002, 21, question(DifficultyLevel.HARD))
            );

            assertEquals("Failed to create question", exception.getMessage());
            assertSame(failure, exception.getCause());
            assertTrue("Rollback missing at execution " + failingExecution,
                    databaseController.isRolledBack());
            assertFalse(databaseController.isCommitted());
            assertTrue(databaseController.isAutoCommit());
        }
    }

    @Test
    public void rollbackAndRestorationFailuresAreSuppressedOnOriginalFailure() {
        SQLException originalFailure = new SQLException("version insert failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.failExecution(2, originalFailure);
        databaseController.setRollbackFailure(rollbackFailure);
        databaseController.setRestorationFailure(restorationFailure);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.create(1002, 21, question(DifficultyLevel.HARD))
        );

        assertSame(originalFailure, exception.getCause());
        assertEquals(2, originalFailure.getSuppressed().length);
        assertSame(rollbackFailure, originalFailure.getSuppressed()[0]);
        assertSame(restorationFailure, originalFailure.getSuppressed()[1]);
    }

    @Test
    public void nullPayloadIsRejectedBeforeOpeningConnection() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> repository.create(1002, 21, null)
        );

        assertEquals("Question data is required", exception.getMessage());
        assertEquals(0, databaseController.getConnectionCalls());
    }

    private static Question question(DifficultyLevel difficulty) {
        return new Question(
                0,
                "  New content  ",
                "  Calculus  ",
                "MULTIPLE_CHOICE",
                difficulty.name(),
                "ACTIVE",
                "images/new.png",
                " One ",
                "Two",
                "Three",
                "Four",
                3
        );
    }

    private static final class RecordingDatabaseController extends DatabaseController {
        private final boolean originalAutoCommit;
        private final List<StatementRecord> statements = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private int connectionCalls;
        private int executionCount;
        private int failingExecution = -1;
        private int generatedQuestionId = 17;
        private boolean generatedKeyAvailable = true;
        private boolean autoCommit;
        private boolean committed;
        private boolean rolledBack;
        private SQLException executionFailure;
        private SQLException rollbackFailure;
        private SQLException restorationFailure;
        private int autoCommitSetCalls;

        private RecordingDatabaseController(boolean originalAutoCommit) {
            this.originalAutoCommit = originalAutoCommit;
            this.autoCommit = originalAutoCommit;
        }

        @Override
        public Connection getConnection() {
            connectionCalls++;
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        switch (method.getName()) {
                            case "getAutoCommit":
                                return autoCommit;
                            case "setAutoCommit":
                                autoCommitSetCalls++;
                                boolean requestedValue = (Boolean) arguments[0];
                                events.add("setAutoCommit:" + requestedValue);
                                if (autoCommitSetCalls > 1 && restorationFailure != null) {
                                    throw restorationFailure;
                                }
                                autoCommit = requestedValue;
                                return null;
                            case "prepareStatement":
                                String sql = (String) arguments[0];
                                int generatedKeysFlag = arguments.length > 1
                                        ? (Integer) arguments[1]
                                        : Statement.NO_GENERATED_KEYS;
                                StatementRecord record = new StatementRecord(
                                        statementName(sql),
                                        sql,
                                        generatedKeysFlag
                                );
                                statements.add(record);
                                return preparedStatement(record);
                            case "commit":
                                events.add("commit");
                                committed = true;
                                return null;
                            case "rollback":
                                events.add("rollback");
                                rolledBack = true;
                                if (rollbackFailure != null) {
                                    throw rollbackFailure;
                                }
                                return null;
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    }
            );
        }

        private PreparedStatement preparedStatement(StatementRecord record) {
            Map<Integer, Object> currentParameters = new HashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        if ("setInt".equals(method.getName())
                                || "setString".equals(method.getName())
                                || "setObject".equals(method.getName())) {
                            currentParameters.put((Integer) arguments[0], arguments[1]);
                            return null;
                        }
                        if ("executeUpdate".equals(method.getName())) {
                            executionCount++;
                            record.getExecutions().add(new HashMap<>(currentParameters));
                            events.add("execute:" + record.getName());
                            if (executionCount == failingExecution) {
                                throw executionFailure;
                            }
                            return 1;
                        }
                        if ("getGeneratedKeys".equals(method.getName())) {
                            return generatedKeys();
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private ResultSet generatedKeys() {
            boolean[] beforeFirst = {true};
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, arguments) -> {
                        if ("next".equals(method.getName())) {
                            if (!beforeFirst[0]) {
                                return false;
                            }
                            beforeFirst[0] = false;
                            return generatedKeyAvailable;
                        }
                        if ("getInt".equals(method.getName())) {
                            return generatedQuestionId;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private String statementName(String sql) {
            if (sql.contains("INSERT INTO questions")) {
                return "question";
            }
            if (sql.contains("INSERT INTO question_versions")) {
                return "version";
            }
            if (sql.contains("INSERT INTO answer_options")) {
                return "option";
            }
            throw new AssertionError("Unexpected SQL");
        }

        private StatementRecord getStatement(String name) {
            return statements.stream()
                    .filter(statement -> statement.getName().equals(name))
                    .findFirst()
                    .orElseThrow();
        }

        private int getConnectionCalls() {
            return connectionCalls;
        }

        private List<String> getEvents() {
            return events;
        }

        private boolean isAutoCommit() {
            return autoCommit;
        }

        private boolean isCommitted() {
            return committed;
        }

        private boolean isRolledBack() {
            return rolledBack;
        }

        private void setGeneratedQuestionId(int generatedQuestionId) {
            this.generatedQuestionId = generatedQuestionId;
        }

        private void setGeneratedKeyAvailable(boolean generatedKeyAvailable) {
            this.generatedKeyAvailable = generatedKeyAvailable;
        }

        private void failExecution(int failingExecution, SQLException executionFailure) {
            this.failingExecution = failingExecution;
            this.executionFailure = executionFailure;
        }

        private void setRollbackFailure(SQLException rollbackFailure) {
            this.rollbackFailure = rollbackFailure;
        }

        private void setRestorationFailure(SQLException restorationFailure) {
            this.restorationFailure = restorationFailure;
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

    private static final class StatementRecord {
        private final String name;
        private final String sql;
        private final int generatedKeysFlag;
        private final List<Map<Integer, Object>> executions = new ArrayList<>();

        private StatementRecord(String name, String sql, int generatedKeysFlag) {
            this.name = name;
            this.sql = sql;
            this.generatedKeysFlag = generatedKeysFlag;
        }

        private String getName() {
            return name;
        }

        private String getSql() {
            return sql;
        }

        private int getGeneratedKeysFlag() {
            return generatedKeysFlag;
        }

        private List<Map<Integer, Object>> getExecutions() {
            return executions;
        }
    }
}
