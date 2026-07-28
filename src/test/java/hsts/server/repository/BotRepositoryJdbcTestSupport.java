package hsts.server.repository;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

final class BotRepositoryJdbcTestSupport {
    private BotRepositoryJdbcTestSupport() {
    }

    static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            row.put((String) pairs[index], pairs[index + 1]);
        }
        return row;
    }

    static Step query(String sqlFragment, Map<String, Object>... rows) {
        return new Step(sqlFragment, List.of(rows), null, 0, null);
    }

    static Step update(String sqlFragment, int count, int generatedId) {
        return new Step(sqlFragment, List.of(), null, count, generatedId);
    }

    static Step failure(String sqlFragment, SQLException failure) {
        return new Step(sqlFragment, List.of(), failure, 0, null);
    }

    static final class Step {
        final String sqlFragment;
        final List<Map<String, Object>> rows;
        final SQLException failure;
        final int updateCount;
        final Integer generatedId;
        final Map<Integer, Object> parameters = new HashMap<>();
        String actualSql;

        private Step(String sqlFragment, List<Map<String, Object>> rows,
                     SQLException failure, int updateCount, Integer generatedId) {
            this.sqlFragment = sqlFragment;
            this.rows = rows;
            this.failure = failure;
            this.updateCount = updateCount;
            this.generatedId = generatedId;
        }
    }

    static final class FakeDatabaseController extends DatabaseController {
        private final Queue<Step> pending = new ArrayDeque<>();
        final List<Step> executed = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        boolean autoCommit = true;
        int commits;
        int rollbacks;
        SQLException rollbackFailure;
        SQLException restoreFailure;

        FakeDatabaseController(Step... steps) {
            pending.addAll(List.of(steps));
        }

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "prepareStatement" -> prepare((String) args[0]);
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> {
                            boolean next = (boolean) args[0];
                            if (next && restoreFailure != null) {
                                throw restoreFailure;
                            }
                            autoCommit = next;
                            events.add("autoCommit:" + next);
                            yield null;
                        }
                        case "commit" -> {
                            commits++;
                            events.add("commit");
                            yield null;
                        }
                        case "rollback" -> {
                            rollbacks++;
                            events.add("rollback");
                            if (rollbackFailure != null) {
                                throw rollbackFailure;
                            }
                            yield null;
                        }
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "unwrap" -> null;
                        case "isWrapperFor" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        void assertConsumed() {
            if (!pending.isEmpty()) {
                throw new AssertionError("Unconsumed JDBC steps: " + pending.size());
            }
        }

        private PreparedStatement prepare(String sql) {
            Step step = pending.poll();
            if (step == null) {
                throw new AssertionError("Unexpected SQL: " + sql);
            }
            if (!sql.contains(step.sqlFragment)) {
                throw new AssertionError(
                        "Expected SQL containing [" + step.sqlFragment + "] but was: " + sql
                );
            }
            step.actualSql = sql;
            executed.add(step);
            events.add("prepare:" + step.sqlFragment);
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setInt", "setString", "setObject" -> {
                            step.parameters.put((Integer) args[0], args[1]);
                            yield null;
                        }
                        case "setNull" -> {
                            step.parameters.put((Integer) args[0], null);
                            yield null;
                        }
                        case "executeQuery" -> {
                            if (step.failure != null) {
                                throw step.failure;
                            }
                            yield resultSet(step.rows);
                        }
                        case "executeUpdate" -> {
                            if (step.failure != null) {
                                throw step.failure;
                            }
                            events.add("update:" + step.sqlFragment);
                            yield step.updateCount;
                        }
                        case "getGeneratedKeys" -> step.generatedId == null
                                ? resultSet(List.of())
                                : resultSet(List.of(row("1", step.generatedId)));
                        case "close" -> null;
                        case "unwrap" -> null;
                        case "isWrapperFor" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] cursor = {-1};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> ++cursor[0] < rows.size();
                    case "getString" -> {
                        Object value = value(rows, cursor[0], args[0]);
                        yield value == null ? null : value.toString();
                    }
                    case "getInt" -> {
                        Object value = value(rows, cursor[0], args[0]);
                        yield value == null ? 0 : ((Number) value).intValue();
                    }
                    case "getBoolean" -> {
                        Object value = value(rows, cursor[0], args[0]);
                        yield value instanceof Boolean booleanValue
                                ? booleanValue
                                : value != null && ((Number) value).intValue() != 0;
                    }
                    case "getObject" -> value(rows, cursor[0], args[0]);
                    case "wasNull" -> false;
                    case "close" -> null;
                    case "unwrap" -> null;
                    case "isWrapperFor" -> false;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object value(List<Map<String, Object>> rows, int cursor, Object key) {
        if (cursor < 0 || cursor >= rows.size()) {
            throw new AssertionError("ResultSet cursor is not on a row");
        }
        Map<String, Object> row = rows.get(cursor);
        if (key instanceof Integer index) {
            String stringKey = Integer.toString(index);
            if (row.containsKey(stringKey)) {
                return row.get(stringKey);
            }
            return row.values().stream().skip(index - 1L).findFirst().orElse(null);
        }
        return row.get(key.toString());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == short.class || type == byte.class
                || type == long.class) {
            return 0;
        }
        if (type == float.class || type == double.class) {
            return 0.0;
        }
        return '\0';
    }
}
