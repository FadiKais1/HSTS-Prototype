package hsts.server.repository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ExamRepositoryJdbcTestSupport {
    private ExamRepositoryJdbcTestSupport() {
    }

    static Map<Object, Object> row(Object... keysAndValues) {
        Map<Object, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            row.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return row;
    }

    static final class FakeDatabaseController extends DatabaseController {
        final List<StatementPlan> plans = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final boolean originalAutoCommit;
        final Connection connection;
        int connectionRequests;
        int autoCommitSetCalls;
        boolean autoCommit;
        int commitCount;
        int rollbackCount;
        SQLException connectionFailure;
        SQLException commitFailure;
        SQLException rollbackFailure;
        SQLException restorationFailure;

        FakeDatabaseController() {
            this(true);
        }

        FakeDatabaseController(boolean originalAutoCommit) {
            this.originalAutoCommit = originalAutoCommit;
            this.autoCommit = originalAutoCommit;
            this.connection = createConnection();
        }

        StatementPlan plan(String sqlMarker) {
            StatementPlan plan = new StatementPlan(sqlMarker);
            plans.add(plan);
            return plan;
        }

        @Override
        public Connection getConnection() throws SQLException {
            connectionRequests++;
            if (connectionFailure != null) {
                throw connectionFailure;
            }
            return connection;
        }

        private Connection createConnection() {
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getAutoCommit":
                        return autoCommit;
                    case "setAutoCommit":
                        autoCommitSetCalls++;
                        if (autoCommitSetCalls > 1 && restorationFailure != null) {
                            throw restorationFailure;
                        }
                        autoCommit = (Boolean) args[0];
                        events.add("autoCommit:" + autoCommit);
                        return null;
                    case "prepareStatement":
                        String sql = (String) args[0];
                        StatementPlan plan = findPlan(sql);
                        plan.preparedCount++;
                        events.add("prepare:" + plan.marker);
                        return createPreparedStatement(plan);
                    case "commit":
                        events.add("commit");
                        commitCount++;
                        if (commitFailure != null) {
                            throw commitFailure;
                        }
                        return null;
                    case "rollback":
                        events.add("rollback");
                        rollbackCount++;
                        if (rollbackFailure != null) {
                            throw rollbackFailure;
                        }
                        return null;
                    case "close":
                        events.add("close");
                        return null;
                    case "isClosed":
                        return false;
                    case "unwrap":
                        return null;
                    case "isWrapperFor":
                        return false;
                    case "toString":
                        return "FakeExamConnection";
                    default:
                        return defaultValue(method.getReturnType());
                }
            };
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    handler
            );
        }

        private StatementPlan findPlan(String sql) throws SQLException {
            String normalizedSql = normalize(sql);
            for (StatementPlan plan : plans) {
                if (normalizedSql.contains(normalize(plan.marker))) {
                    plan.sql = sql;
                    return plan;
                }
            }
            throw new SQLException("Unexpected SQL: " + sql);
        }

        private PreparedStatement createPreparedStatement(StatementPlan plan) {
            Map<Integer, Object> parameters = new HashMap<>();
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length >= 2
                        && args[0] instanceof Integer index) {
                    parameters.put(index, "setNull".equals(name) ? null : args[1]);
                    return null;
                }

                switch (name) {
                    case "executeQuery":
                        plan.queryExecutions.add(new HashMap<>(parameters));
                        events.add("query:" + plan.marker);
                        Object queryOutcome = plan.queryOutcomes.isEmpty()
                                ? List.of() : plan.queryOutcomes.removeFirst();
                        if (queryOutcome instanceof SQLException failure) {
                            throw failure;
                        }
                        return createResultSet(castRows(queryOutcome));
                    case "executeUpdate":
                        plan.updateExecutions.add(new HashMap<>(parameters));
                        events.add("update:" + plan.marker);
                        Object updateOutcome = plan.updateOutcomes.isEmpty()
                                ? Integer.valueOf(1) : plan.updateOutcomes.removeFirst();
                        if (updateOutcome instanceof SQLException failure) {
                            throw failure;
                        }
                        return updateOutcome;
                    case "getGeneratedKeys":
                        List<Map<Object, Object>> generatedRows = plan.generatedKeyRows.isEmpty()
                                ? List.of() : plan.generatedKeyRows.removeFirst();
                        return createResultSet(generatedRows);
                    case "close":
                        return null;
                    case "unwrap":
                        return null;
                    case "isWrapperFor":
                        return false;
                    case "toString":
                        return "FakeExamPreparedStatement[" + plan.marker + "]";
                    default:
                        return defaultValue(method.getReturnType());
                }
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    handler
            );
        }

        private ResultSet createResultSet(List<Map<Object, Object>> rows) {
            InvocationHandler handler = new InvocationHandler() {
                private int rowIndex = -1;
                private boolean lastWasNull;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    switch (method.getName()) {
                        case "next":
                            rowIndex++;
                            return rowIndex < rows.size();
                        case "getInt":
                            Object intValue = value(rows, rowIndex, args[0]);
                            lastWasNull = intValue == null;
                            return intValue == null ? 0 : ((Number) intValue).intValue();
                        case "getDouble":
                            Object doubleValue = value(rows, rowIndex, args[0]);
                            lastWasNull = doubleValue == null;
                            return doubleValue == null ? 0.0 : ((Number) doubleValue).doubleValue();
                        case "getString":
                            Object stringValue = value(rows, rowIndex, args[0]);
                            lastWasNull = stringValue == null;
                            return stringValue == null ? null : stringValue.toString();
                        case "getBigDecimal":
                            Object decimalValue = value(rows, rowIndex, args[0]);
                            lastWasNull = decimalValue == null;
                            if (decimalValue == null || decimalValue instanceof BigDecimal) {
                                return decimalValue;
                            }
                            return BigDecimal.valueOf(((Number) decimalValue).doubleValue());
                        case "getObject":
                            Object objectValue = value(rows, rowIndex, args[0]);
                            lastWasNull = objectValue == null;
                            return objectValue;
                        case "wasNull":
                            return lastWasNull;
                        case "close":
                            return null;
                        case "unwrap":
                            return null;
                        case "isWrapperFor":
                            return false;
                        case "toString":
                            return "FakeExamResultSet";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                }
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    handler
            );
        }

        private Object value(List<Map<Object, Object>> rows, int rowIndex, Object key) {
            return rows.get(rowIndex).get(key);
        }

        @SuppressWarnings("unchecked")
        private List<Map<Object, Object>> castRows(Object outcome) {
            return (List<Map<Object, Object>>) outcome;
        }
    }

    static final class StatementPlan {
        final String marker;
        String sql;
        int preparedCount;
        final Deque<Object> queryOutcomes = new ArrayDeque<>();
        final Deque<Object> updateOutcomes = new ArrayDeque<>();
        final Deque<List<Map<Object, Object>>> generatedKeyRows = new ArrayDeque<>();
        final List<Map<Integer, Object>> queryExecutions = new ArrayList<>();
        final List<Map<Integer, Object>> updateExecutions = new ArrayList<>();

        StatementPlan(String marker) {
            this.marker = marker;
        }

        @SafeVarargs
        final StatementPlan queryRows(Map<Object, Object>... rows) {
            queryOutcomes.addLast(List.of(rows));
            return this;
        }

        StatementPlan queryFailure(SQLException failure) {
            queryOutcomes.addLast(failure);
            return this;
        }

        StatementPlan updateResults(int... results) {
            for (int result : results) {
                updateOutcomes.addLast(result);
            }
            return this;
        }

        StatementPlan updateFailure(SQLException failure) {
            updateOutcomes.addLast(failure);
            return this;
        }

        StatementPlan generatedKey(int generatedId) {
            generatedKeyRows.addLast(List.of(row(1, generatedId)));
            return this;
        }

        StatementPlan noGeneratedKey() {
            generatedKeyRows.addLast(List.of());
            return this;
        }
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
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
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
