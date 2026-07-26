package hsts.server.repository;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UserRepositoryTest {
    @Test
    public void findByEmailNormalizesLookupAndMapsEveryUserAttribute() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(userRow());
        UserRepository repository = new UserRepository(databaseController);

        Optional<User> result = repository.findByEmail("  STUDENT@HSTS.LOCAL  ");

        assertTrue(result.isPresent());
        User user = result.get();
        assertEquals(1001, user.getUserId());
        assertEquals("Development Student", user.getFullName());
        assertEquals("student@hsts.local", user.getEmail());
        assertEquals("stored-password-hash", user.getPasswordHash());
        assertEquals(UserRole.STUDENT, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(
                "SELECT user_id, full_name, email, password_hash, role, status "
                        + "FROM users WHERE email = ?",
                databaseController.getSql()
        );
        assertEquals("student@hsts.local", databaseController.getParameter(1));
    }

    @Test
    public void nullEmailReturnsEmptyWithoutOpeningConnection() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(userRow());
        UserRepository repository = new UserRepository(databaseController);

        assertFalse(repository.findByEmail(null).isPresent());
        assertEquals(0, databaseController.getConnectionCalls());
    }

    @Test
    public void findByIdReturnsEmptyAndUsesPreparedParameter() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(null);
        UserRepository repository = new UserRepository(databaseController);

        Optional<User> result = repository.findById(9999);

        assertFalse(result.isPresent());
        assertEquals(
                "SELECT user_id, full_name, email, password_hash, role, status "
                        + "FROM users WHERE user_id = ?",
                databaseController.getSql()
        );
        assertEquals(9999, databaseController.getParameter(1));
    }

    @Test
    public void updateStatusUsesEnumNameAndReturnsUpdateResult() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(null);
        databaseController.setUpdateCount(1);
        UserRepository repository = new UserRepository(databaseController);

        assertTrue(repository.updateStatus(1001, UserStatus.BLOCKED));
        assertEquals(
                "UPDATE users SET status = ? WHERE user_id = ?",
                databaseController.getSql()
        );
        assertEquals("BLOCKED", databaseController.getParameter(1));
        assertEquals(1001, databaseController.getParameter(2));
    }

    @Test
    public void nullStatusIsRejectedWithoutOpeningConnection() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(null);
        UserRepository repository = new UserRepository(databaseController);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.updateStatus(1001, null)
        );
        assertEquals(0, databaseController.getConnectionCalls());
    }

    @Test
    public void sqlFailureIsWrappedWithOriginalCause() {
        SQLException sqlException = new SQLException("database unavailable");
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(null);
        databaseController.setConnectionFailure(sqlException);
        UserRepository repository = new UserRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findById(1001)
        );

        assertEquals("Failed to load user by id", exception.getMessage());
        assertSame(sqlException, exception.getCause());
    }

    private static Map<String, Object> userRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", 1001);
        row.put("full_name", "Development Student");
        row.put("email", "student@hsts.local");
        row.put("password_hash", "stored-password-hash");
        row.put("role", "STUDENT");
        row.put("status", "ACTIVE");
        return row;
    }

    private static final class RecordingDatabaseController extends DatabaseController {
        private final Map<String, Object> row;
        private final Map<Integer, Object> parameters = new HashMap<>();
        private int connectionCalls;
        private int updateCount;
        private String sql;
        private SQLException connectionFailure;

        private RecordingDatabaseController(Map<String, Object> row) {
            this.row = row;
        }

        @Override
        public Connection getConnection() throws SQLException {
            connectionCalls++;
            if (connectionFailure != null) {
                throw connectionFailure;
            }

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
                        if ("setString".equals(method.getName())
                                || "setInt".equals(method.getName())) {
                            parameters.put((Integer) arguments[0], arguments[1]);
                            return null;
                        }
                        if ("executeQuery".equals(method.getName())) {
                            return resultSet();
                        }
                        if ("executeUpdate".equals(method.getName())) {
                            return updateCount;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private ResultSet resultSet() {
            boolean[] beforeFirst = {true};
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, arguments) -> {
                        if ("next".equals(method.getName())) {
                            if (beforeFirst[0]) {
                                beforeFirst[0] = false;
                                return row != null;
                            }
                            return false;
                        }
                        if ("getInt".equals(method.getName())) {
                            return (Integer) row.get((String) arguments[0]);
                        }
                        if ("getString".equals(method.getName())) {
                            return (String) row.get((String) arguments[0]);
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private String getSql() {
            return sql;
        }

        private Object getParameter(int index) {
            return parameters.get(index);
        }

        private int getConnectionCalls() {
            return connectionCalls;
        }

        private void setUpdateCount(int updateCount) {
            this.updateCount = updateCount;
        }

        private void setConnectionFailure(SQLException connectionFailure) {
            this.connectionFailure = connectionFailure;
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
