package hsts.server.repository;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QuestionRepositoryEntityJdbcTestSupport {
    private QuestionRepositoryEntityJdbcTestSupport() {
    }

    static final class Controller extends DatabaseController {
        private final List<StatementRecord> statements = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private List<Map<String, Object>> queryRows = List.of();
        private boolean autoCommit;
        private int connectionCalls;
        private int executionCount;
        private int failingExecution = -1;
        private int generatedQuestionId = 42;
        private Integer currentVersion = 4;
        private SQLException queryFailure;
        private SQLException executionFailure;
        private SQLException rollbackFailure;
        private SQLException restorationFailure;
        private int autoCommitSetCalls;
        private boolean committed;
        private boolean rolledBack;

        Controller(boolean originalAutoCommit) {
            autoCommit = originalAutoCommit;
        }

        @Override
        public Connection getConnection() {
            connectionCalls++;
            return proxy(Connection.class, (method, arguments) -> {
                return switch (method) {
                    case "getAutoCommit" -> autoCommit;
                    case "setAutoCommit" -> {
                        autoCommitSetCalls++;
                        boolean requested = (Boolean) arguments[0];
                        events.add("setAutoCommit:" + requested);
                        if (autoCommitSetCalls > 1 && restorationFailure != null) {
                            throw restorationFailure;
                        }
                        autoCommit = requested;
                        yield null;
                    }
                    case "prepareStatement" -> {
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
                        yield preparedStatement(record);
                    }
                    case "commit" -> {
                        events.add("commit");
                        committed = true;
                        yield null;
                    }
                    case "rollback" -> {
                        events.add("rollback");
                        rolledBack = true;
                        if (rollbackFailure != null) {
                            throw rollbackFailure;
                        }
                        yield null;
                    }
                    default -> defaultValue(returnType(method));
                };
            });
        }

        private PreparedStatement preparedStatement(StatementRecord record) {
            Map<Integer, Object> currentParameters = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (method, arguments) -> {
                if (method.startsWith("set") && arguments != null
                        && arguments.length >= 2 && arguments[0] instanceof Integer) {
                    currentParameters.put((Integer) arguments[0], arguments[1]);
                    return null;
                }
                return switch (method) {
                    case "executeQuery" -> {
                        record.executions.add(new LinkedHashMap<>(currentParameters));
                        events.add("query:" + record.name);
                        if (queryFailure != null) {
                            throw queryFailure;
                        }
                        if ("lock".equals(record.name)) {
                            yield currentVersion == null
                                    ? resultSet(List.of())
                                    : resultSet(List.of(Map.of(
                                            "current_version_no",
                                            currentVersion
                                    )));
                        }
                        yield resultSet(queryRows);
                    }
                    case "executeUpdate" -> {
                        executionCount++;
                        record.executions.add(new LinkedHashMap<>(currentParameters));
                        events.add("execute:" + record.name);
                        if (executionCount == failingExecution) {
                            throw executionFailure;
                        }
                        yield 1;
                    }
                    case "getGeneratedKeys" -> resultSet(List.of(Map.of(
                            1,
                            generatedQuestionId
                    )));
                    default -> defaultValue(returnType(method));
                };
            });
        }

        private ResultSet resultSet(List<? extends Map<?, ?>> rows) {
            int[] index = {-1};
            return proxy(ResultSet.class, (method, arguments) -> {
                if ("close".equals(method)) {
                    return null;
                }
                if ("next".equals(method)) {
                    index[0]++;
                    return index[0] < rows.size();
                }
                if (index[0] < 0 || index[0] >= rows.size()) {
                    return defaultValue(returnType(method));
                }
                Map<?, ?> row = rows.get(index[0]);
                Object key = arguments == null || arguments.length == 0
                        ? null
                        : arguments[0];
                Object value = row.get(key);
                return switch (method) {
                    case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                    case "getString" -> value == null ? null : value.toString();
                    case "getObject" -> value;
                    default -> defaultValue(returnType(method));
                };
            });
        }

        private String statementName(String sql) {
            if (sql.contains("SELECT q.question_id")
                    && sql.contains("option_row.option_number")) {
                return "entity-read";
            }
            if (sql.contains("SELECT current_version_no")) {
                return "lock";
            }
            if (sql.contains("INSERT INTO questions")) {
                return "question";
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

        StatementRecord statement(String name) {
            return statements.stream()
                    .filter(statement -> statement.name.equals(name))
                    .findFirst()
                    .orElseThrow();
        }

        void setQueryRows(List<Map<String, Object>> queryRows) {
            this.queryRows = queryRows;
        }

        void setCurrentVersion(Integer currentVersion) {
            this.currentVersion = currentVersion;
        }

        void setGeneratedQuestionId(int generatedQuestionId) {
            this.generatedQuestionId = generatedQuestionId;
        }

        void failQuery(SQLException queryFailure) {
            this.queryFailure = queryFailure;
        }

        void failExecution(int failingExecution, SQLException executionFailure) {
            this.failingExecution = failingExecution;
            this.executionFailure = executionFailure;
        }

        void setRollbackFailure(SQLException rollbackFailure) {
            this.rollbackFailure = rollbackFailure;
        }

        void setRestorationFailure(SQLException restorationFailure) {
            this.restorationFailure = restorationFailure;
        }

        int getConnectionCalls() {
            return connectionCalls;
        }

        List<String> getEvents() {
            return events;
        }

        boolean isAutoCommit() {
            return autoCommit;
        }

        boolean isCommitted() {
            return committed;
        }

        boolean isRolledBack() {
            return rolledBack;
        }
    }

    static final class StatementRecord {
        private final String name;
        private final String sql;
        private final int generatedKeysFlag;
        private final List<Map<Integer, Object>> executions = new ArrayList<>();

        private StatementRecord(String name, String sql, int generatedKeysFlag) {
            this.name = name;
            this.sql = sql;
            this.generatedKeysFlag = generatedKeysFlag;
        }

        String getSql() {
            return sql;
        }

        int getGeneratedKeysFlag() {
            return generatedKeysFlag;
        }

        List<Map<Integer, Object>> getExecutions() {
            return executions;
        }
    }

    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> type.getSimpleName() + "Proxy";
                            default -> throw new AssertionError(
                                    "Unexpected Object method: " + method.getName()
                            );
                        };
                    }
                    return invocation.invoke(method.getName(), arguments);
                }
        );
    }

    private static Class<?> returnType(String methodName) {
        return switch (methodName) {
            case "getAutoCommit", "next" -> boolean.class;
            case "executeUpdate", "getInt" -> int.class;
            default -> Object.class;
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        return null;
    }
}
