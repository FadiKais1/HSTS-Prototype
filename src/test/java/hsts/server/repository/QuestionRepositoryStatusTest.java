package hsts.server.repository;

import hsts.common.type.QuestionStatus;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestionRepositoryStatusTest {
    @Test
    public void nullStatusIsRejectedBeforeOpeningConnection() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> repository.updateStatusForTeacher(1002, 17, null)
        );

        assertEquals("Question status is required", exception.getMessage());
        assertEquals(0, databaseController.getConnectionCalls());
    }

    @Test
    public void assignedQuestionChangesOnlyStatusAndCommits() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setCurrentStatus("ACTIVE");
        QuestionRepository repository = new QuestionRepository(databaseController);

        assertTrue(repository.updateStatusForTeacher(1002, 17, QuestionStatus.INACTIVE));

        assertEquals(1, databaseController.getConnectionCalls());
        assertTrue(databaseController.isCommitted());
        assertFalse(databaseController.isRolledBack());
        assertTrue(databaseController.isAutoCommit());

        StatementRecord lock = databaseController.getStatement("lock");
        assertContainsNormalized(lock.getSql(),
                "SELECT q.status",
                "JOIN teacher_courses tc ON tc.course_id = q.course_id "
                        + "AND tc.teacher_user_id = ?",
                "WHERE q.question_id = ?",
                "FOR UPDATE"
        );
        assertEquals(1002, lock.getParameters().get(1));
        assertEquals(17, lock.getParameters().get(2));

        StatementRecord update = databaseController.getStatement("update");
        assertContainsNormalized(update.getSql(),
                "UPDATE questions q",
                "JOIN teacher_courses tc ON tc.course_id = q.course_id",
                "SET q.status = ?",
                "WHERE q.question_id = ? AND tc.teacher_user_id = ?"
        );
        assertEquals("INACTIVE", update.getParameters().get(1));
        assertEquals(17, update.getParameters().get(2));
        assertEquals(1002, update.getParameters().get(3));

        String normalizedUpdate = normalizeSql(update.getSql());
        assertFalse(normalizedUpdate.contains("current_version_no"));
        assertFalse(normalizedUpdate.contains("content ="));
        assertFalse(normalizedUpdate.contains("topic ="));
        assertFalse(normalizedUpdate.contains("difficulty ="));
        assertFalse(normalizedUpdate.contains("updated_at"));
        assertFalse(normalizedUpdate.contains("INSERT INTO question_versions"));
        assertFalse(normalizedUpdate.contains("INSERT INTO answer_options"));

        assertEquals(List.of(
                "setAutoCommit:false",
                "query:lock",
                "execute:update",
                "commit",
                "setAutoCommit:true"
        ), databaseController.getEvents());
    }

    @Test
    public void alreadyMatchingStatusIsSuccessfulWithoutUpdate() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setCurrentStatus("INACTIVE");
        QuestionRepository repository = new QuestionRepository(databaseController);

        assertTrue(repository.updateStatusForTeacher(1003, 18, QuestionStatus.INACTIVE));

        assertTrue(databaseController.isCommitted());
        assertFalse(databaseController.isRolledBack());
        assertEquals(1, databaseController.getStatements().size());
        assertEquals(List.of(
                "setAutoCommit:false",
                "query:lock",
                "commit",
                "setAutoCommit:true"
        ), databaseController.getEvents());
    }

    @Test
    public void missingOrUnassignedQuestionReturnsFalse() {
        for (int questionId : new int[]{404, 405}) {
            RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
            databaseController.setAccessible(false);
            QuestionRepository repository = new QuestionRepository(databaseController);

            assertFalse(repository.updateStatusForTeacher(
                    1002,
                    questionId,
                    QuestionStatus.ACTIVE
            ));

            StatementRecord lock = databaseController.getStatement("lock");
            assertEquals(1002, lock.getParameters().get(1));
            assertEquals(questionId, lock.getParameters().get(2));
            assertTrue(databaseController.isCommitted());
            assertFalse(databaseController.isRolledBack());
            assertEquals(1, databaseController.getStatements().size());
        }
    }

    @Test
    public void originallyDisabledAutoCommitIsRestored() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(false);
        databaseController.setCurrentStatus("ACTIVE");
        QuestionRepository repository = new QuestionRepository(databaseController);

        assertTrue(repository.updateStatusForTeacher(1002, 17, QuestionStatus.INACTIVE));

        assertFalse(databaseController.isAutoCommit());
        assertEquals("setAutoCommit:false", databaseController.getEvents().get(0));
        assertEquals("setAutoCommit:false", databaseController.getEvents().get(4));
    }

    @Test
    public void queryOrUpdateSqlFailureRollsBackAndPreservesCause() {
        for (boolean failQuery : new boolean[]{true, false}) {
            SQLException failure = new SQLException(
                    failQuery ? "status lock failed" : "status update failed"
            );
            RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
            databaseController.setCurrentStatus("ACTIVE");
            if (failQuery) {
                databaseController.setQueryFailure(failure);
            } else {
                databaseController.setUpdateFailure(failure);
            }
            QuestionRepository repository = new QuestionRepository(databaseController);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> repository.updateStatusForTeacher(
                            1002,
                            17,
                            QuestionStatus.INACTIVE
                    )
            );

            assertEquals("Failed to update question status", exception.getMessage());
            assertSame(failure, exception.getCause());
            assertTrue(databaseController.isRolledBack());
            assertFalse(databaseController.isCommitted());
            assertTrue(databaseController.isAutoCommit());
        }
    }

    @Test
    public void rollbackAndRestorationFailuresAreSuppressedOnOriginalCause() {
        SQLException originalFailure = new SQLException("status update failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        RecordingDatabaseController databaseController = new RecordingDatabaseController(true);
        databaseController.setCurrentStatus("ACTIVE");
        databaseController.setUpdateFailure(originalFailure);
        databaseController.setRollbackFailure(rollbackFailure);
        databaseController.setRestorationFailure(restorationFailure);
        QuestionRepository repository = new QuestionRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.updateStatusForTeacher(1002, 17, QuestionStatus.INACTIVE)
        );

        assertSame(originalFailure, exception.getCause());
        assertEquals(2, originalFailure.getSuppressed().length);
        assertSame(rollbackFailure, originalFailure.getSuppressed()[0]);
        assertSame(restorationFailure, originalFailure.getSuppressed()[1]);
    }

    private static void assertContainsNormalized(String sql, String... fragments) {
        String normalizedSql = normalizeSql(sql);
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
        private final List<StatementRecord> statements = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private int connectionCalls;
        private boolean accessible = true;
        private String currentStatus = "ACTIVE";
        private boolean autoCommit;
        private boolean committed;
        private boolean rolledBack;
        private SQLException queryFailure;
        private SQLException updateFailure;
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
                                String sql = (String) arguments[0];
                                StatementRecord statement = new StatementRecord(
                                        sql.contains("SELECT q.status") ? "lock" : "update",
                                        sql
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
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        if ("setInt".equals(method.getName())
                                || "setString".equals(method.getName())) {
                            statement.getParameters().put((Integer) arguments[0], arguments[1]);
                            return null;
                        }
                        if ("executeQuery".equals(method.getName())) {
                            events.add("query:" + statement.getName());
                            if (queryFailure != null) {
                                throw queryFailure;
                            }
                            return statusResultSet();
                        }
                        if ("executeUpdate".equals(method.getName())) {
                            events.add("execute:" + statement.getName());
                            if (updateFailure != null) {
                                throw updateFailure;
                            }
                            return 1;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private ResultSet statusResultSet() {
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
                            return accessible;
                        }
                        if ("getString".equals(method.getName())) {
                            assertEquals("status", arguments[0]);
                            return currentStatus;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
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

        private void setAccessible(boolean accessible) {
            this.accessible = accessible;
        }

        private void setCurrentStatus(String currentStatus) {
            this.currentStatus = currentStatus;
        }

        private void setQueryFailure(SQLException queryFailure) {
            this.queryFailure = queryFailure;
        }

        private void setUpdateFailure(SQLException updateFailure) {
            this.updateFailure = updateFailure;
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
        private final Map<Integer, Object> parameters = new HashMap<>();

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

        private Map<Integer, Object> getParameters() {
            return parameters;
        }
    }
}
