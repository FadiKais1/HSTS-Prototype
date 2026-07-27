package hsts.server.repository;

import hsts.common.UpdateQuestionPayload;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

public class QuestionRepositoryVersionedUpdateTest {
    @Test
    public void updateLocksMapsEveryRowAndCommitsAfterCurrentRowUpdate() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setCurrentVersionNo(4);
        QuestionRepository repository = new QuestionRepository(databaseController);
        UpdateQuestionPayload payload = payload(4);

        int newVersionNo = repository.updateWithNewVersion(1002, payload);

        assertEquals(5, newVersionNo);
        assertEquals(1, databaseController.getConnectionCalls());
        assertTrue(databaseController.isCommitted());
        assertFalse(databaseController.isRolledBack());
        assertTrue(databaseController.isAutoCommit());

        StatementRecord lock = databaseController.getStatement("lock");
        assertTrue(lock.getSql().contains("SELECT current_version_no"));
        assertTrue(lock.getSql().contains("WHERE question_id = ?"));
        assertTrue(lock.getSql().contains("FOR UPDATE"));
        assertEquals(27, lock.getExecutions().get(0).get(1));

        StatementRecord version = databaseController.getStatement("version");
        Map<Integer, Object> versionParameters = version.getExecutions().get(0);
        assertEquals(27, versionParameters.get(1));
        assertEquals(5, versionParameters.get(2));
        assertEquals("  Updated content  ", versionParameters.get(3));
        assertEquals("  Calculus  ", versionParameters.get(4));
        assertEquals("MULTIPLE_CHOICE", versionParameters.get(5));
        assertEquals("HARD", versionParameters.get(6));
        assertEquals("images/updated.png", versionParameters.get(7));
        assertEquals(3, versionParameters.get(8));
        assertEquals(1002, versionParameters.get(9));

        StatementRecord option = databaseController.getStatement("option");
        assertEquals(4, option.getExecutions().size());
        String[] optionTexts = {" One ", "Two", "Three", "Four"};
        for (int index = 0; index < optionTexts.length; index++) {
            Map<Integer, Object> optionParameters = option.getExecutions().get(index);
            assertEquals(27, optionParameters.get(1));
            assertEquals(5, optionParameters.get(2));
            assertEquals(index + 1, optionParameters.get(3));
            assertEquals(optionTexts[index], optionParameters.get(4));
        }

        StatementRecord current = databaseController.getStatement("current");
        Map<Integer, Object> currentParameters = current.getExecutions().get(0);
        assertEquals("  Updated content  ", currentParameters.get(1));
        assertEquals("  Calculus  ", currentParameters.get(2));
        assertEquals("MULTIPLE_CHOICE", currentParameters.get(3));
        assertEquals("HARD", currentParameters.get(4));
        assertEquals("images/updated.png", currentParameters.get(5));
        assertEquals(" One ", currentParameters.get(6));
        assertEquals("Two", currentParameters.get(7));
        assertEquals("Three", currentParameters.get(8));
        assertEquals("Four", currentParameters.get(9));
        assertEquals(3, currentParameters.get(10));
        assertEquals(5, currentParameters.get(11));
        assertEquals(27, currentParameters.get(13));
        assertEquals(4, currentParameters.get(14));
        assertFalse(current.getSql().contains("status"));
        assertFalse(current.getSql().contains("course_id"));
        assertFalse(current.getSql().contains("created_by_user_id"));
        assertFalse(current.getSql().contains("created_at"));

        Object updatedAt = versionParameters.get(10);
        assertTrue(updatedAt instanceof LocalDateTime);
        assertSame(updatedAt, currentParameters.get(12));

        assertEquals(List.of(
                "setAutoCommit:false",
                "query:lock",
                "execute:version",
                "execute:option",
                "execute:option",
                "execute:option",
                "execute:option",
                "execute:current",
                "commit",
                "setAutoCommit:true"
        ), databaseController.getEvents());
    }

    @Test
    public void expectedVersionZeroAcceptsLockedCurrentVersion() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(false);
        databaseController.setCurrentVersionNo(8);
        QuestionRepository repository = new QuestionRepository(databaseController);

        assertEquals(9, repository.updateWithNewVersion(1003, payload(0)));
        assertFalse(databaseController.isAutoCommit());
        assertEquals("setAutoCommit:false", databaseController.getEvents().get(0));
        assertEquals("setAutoCommit:false", databaseController.getEvents().get(9));
    }

    @Test
    public void staleExpectedVersionConflictsBeforeAnyInsert() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setCurrentVersionNo(5);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.updateWithNewVersion(1002, payload(4))
        );

        assertEquals("Question version conflict", exception.getMessage());
        assertEquals(null, exception.getCause());
        assertTrue(databaseController.isRolledBack());
        assertFalse(databaseController.isCommitted());
        assertEquals(List.of(
                "setAutoCommit:false",
                "query:lock",
                "rollback",
                "setAutoCommit:true"
        ), databaseController.getEvents());
        assertEquals(1, databaseController.getStatements().size());
    }

    @Test
    public void missingQuestionPreservesDomainErrorAndRollsBack() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setQuestionExists(false);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> repository.updateWithNewVersion(1002, payload(0))
        );

        assertEquals("Question not found: 27", exception.getMessage());
        assertEquals(null, exception.getCause());
        assertTrue(databaseController.isRolledBack());
        assertFalse(databaseController.isCommitted());
        assertEquals(1, databaseController.getStatements().size());
    }

    @Test
    public void failureAtVersionEachOptionOrCurrentUpdateRollsBack() {
        for (int failingExecution = 1; failingExecution <= 6; failingExecution++) {
            SQLException failure = new SQLException("write step failed " + failingExecution);
            RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
            databaseController.setCurrentVersionNo(4);
            databaseController.failExecution(failingExecution, failure);
            QuestionRepository repository = new QuestionRepository(databaseController);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> repository.updateWithNewVersion(1002, payload(4))
            );

            assertEquals("Failed to update question version", exception.getMessage());
            assertSame(failure, exception.getCause());
            assertTrue("Rollback missing at execution " + failingExecution,
                    databaseController.isRolledBack());
            assertFalse(databaseController.isCommitted());
            assertTrue(databaseController.isAutoCommit());
        }
    }

    @Test
    public void zeroRowCurrentUpdatePreservesConflictAndRollsBack() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setCurrentVersionNo(4);
        databaseController.setCurrentUpdateRows(0);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.updateWithNewVersion(1002, payload(4))
        );

        assertEquals("Question version conflict", exception.getMessage());
        assertEquals(null, exception.getCause());
        assertTrue(databaseController.isRolledBack());
        assertFalse(databaseController.isCommitted());
        assertTrue(databaseController.isAutoCommit());
    }

    @Test
    public void rollbackAndRestorationFailuresAreSuppressedOnOriginalSqlFailure() {
        SQLException originalFailure = new SQLException("version insert failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.failExecution(1, originalFailure);
        databaseController.setRollbackFailure(rollbackFailure);
        databaseController.setRestorationFailure(restorationFailure);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.updateWithNewVersion(1002, payload(0))
        );

        assertEquals("Failed to update question version", exception.getMessage());
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
                () -> repository.updateWithNewVersion(1002, null)
        );

        assertEquals("Question update data is required", exception.getMessage());
        assertEquals(0, databaseController.getConnectionCalls());
    }

    private static UpdateQuestionPayload payload(int expectedVersionNo) {
        return new UpdateQuestionPayload(
                27,
                "  Updated content  ",
                "  Calculus  ",
                "HARD",
                "INACTIVE",
                "images/updated.png",
                " One ",
                "Two",
                "Three",
                "Four",
                3,
                expectedVersionNo
        );
    }

    private static final class RecordingDatabaseController extends DatabaseController {
        private final List<StatementRecord> statements = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private int connectionCalls;
        private int currentVersionNo = 1;
        private int currentUpdateRows = 1;
        private int executionCount;
        private int failingExecution = -1;
        private boolean questionExists = true;
        private boolean autoCommit;
        private boolean committed;
        private boolean rolledBack;
        private SQLException executionFailure;
        private SQLException rollbackFailure;
        private SQLException restorationFailure;
        private int autoCommitSetCalls;

        private RecordingDatabaseController(boolean originalAutoCommit) {
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
                                StatementRecord statement = new StatementRecord(
                                        statementName((String) arguments[0]),
                                        (String) arguments[0]
                                );
                                statements.add(statement);
                                return preparedStatement(statement);
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

        private PreparedStatement preparedStatement(StatementRecord statement) {
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
                        if ("executeQuery".equals(method.getName())) {
                            statement.getExecutions().add(new HashMap<>(currentParameters));
                            events.add("query:" + statement.getName());
                            return lockedVersionResultSet();
                        }
                        if ("executeUpdate".equals(method.getName())) {
                            executionCount++;
                            statement.getExecutions().add(new HashMap<>(currentParameters));
                            events.add("execute:" + statement.getName());
                            if (executionCount == failingExecution) {
                                throw executionFailure;
                            }
                            if ("current".equals(statement.getName())) {
                                return currentUpdateRows;
                            }
                            return 1;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private ResultSet lockedVersionResultSet() {
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
                            return questionExists;
                        }
                        if ("getInt".equals(method.getName())) {
                            assertEquals("current_version_no", arguments[0]);
                            return currentVersionNo;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private String statementName(String sql) {
            if (sql.contains("SELECT current_version_no")) {
                return "lock";
            }
            if (sql.contains("INSERT INTO question_versions")) {
                return "version";
            }
            if (sql.contains("INSERT INTO answer_options")) {
                return "option";
            }
            if (sql.contains("UPDATE questions")) {
                return "current";
            }
            throw new AssertionError("Unexpected SQL: " + sql);
        }

        private StatementRecord getStatement(String name) {
            return statements.stream()
                    .filter(statement -> statement.getName().equals(name))
                    .findFirst()
                    .orElseThrow();
        }

        private List<StatementRecord> getStatements() {
            return statements;
        }

        private List<String> getEvents() {
            return events;
        }

        private int getConnectionCalls() {
            return connectionCalls;
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

        private void setCurrentVersionNo(int currentVersionNo) {
            this.currentVersionNo = currentVersionNo;
        }

        private void setCurrentUpdateRows(int currentUpdateRows) {
            this.currentUpdateRows = currentUpdateRows;
        }

        private void setQuestionExists(boolean questionExists) {
            this.questionExists = questionExists;
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
        private final List<Map<Integer, Object>> executions = new ArrayList<>();

        private StatementRecord(String name, String sql) {
            this.name = name;
            this.sql = sql;
        }

        private String getName() {
            return name;
        }

        private String getSql() {
            return sql;
        }

        private List<Map<Integer, Object>> getExecutions() {
            return executions;
        }
    }
}
