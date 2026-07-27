package hsts.server.repository;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.Coordinator;
import hsts.server.entity.Principal;
import hsts.server.entity.Student;
import hsts.server.entity.Teacher;
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
    public void everyDatabaseRoleMapsToItsDiagramSubtypeWithExactBaseFields() {
        Map<UserRole, Class<? extends User>> expectedTypes = new LinkedHashMap<>();
        expectedTypes.put(UserRole.STUDENT, Student.class);
        expectedTypes.put(UserRole.TEACHER, Teacher.class);
        expectedTypes.put(UserRole.COORDINATOR, Coordinator.class);
        expectedTypes.put(UserRole.PRINCIPAL, Principal.class);

        int userId = 2000;
        for (Map.Entry<UserRole, Class<? extends User>> expected : expectedTypes.entrySet()) {
            userId++;
            RecordingDatabaseController databaseController =
                    new RecordingDatabaseController(userRow(
                            userId,
                            expected.getKey().name(),
                            userId % 2 == 0 ? "ACTIVE" : "BLOCKED"
                    ));

            User user = new UserRepository(databaseController)
                    .findById(userId)
                    .orElseThrow();

            assertEquals(expected.getValue(), user.getClass());
            if (expected.getKey() == UserRole.COORDINATOR) {
                assertTrue(user instanceof Teacher);
            }
            assertEquals(userId, user.getUserId());
            assertEquals("Development User " + userId, user.getFullName());
            assertEquals("user" + userId + "@hsts.local", user.getEmail());
            assertEquals("stored-password-hash-" + userId, user.getPasswordHash());
            assertEquals(expected.getKey(), user.getRole());
            assertEquals(
                    userId % 2 == 0 ? UserStatus.ACTIVE : UserStatus.BLOCKED,
                    user.getStatus()
            );
        }
    }

    @Test
    public void findByEmailNormalizesLookupAndMapsEveryUserAttribute() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(userRow());
        UserRepository repository = new UserRepository(databaseController);

        Optional<User> result = repository.findByEmail("  STUDENT@HSTS.LOCAL  ");

        assertTrue(result.isPresent());
        User user = result.get();
        assertEquals(Student.class, user.getClass());
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

    @Test
    public void invalidAndNullRolePreserveEnumParsingFailures() {
        Map<String, Object> invalidRole = userRow(3001, "UNKNOWN", "ACTIVE");
        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> new UserRepository(new RecordingDatabaseController(invalidRole))
                        .findById(3001)
        );
        assertEquals("No enum constant hsts.common.type.UserRole.UNKNOWN",
                invalid.getMessage());

        Map<String, Object> nullRole = userRow(3002, null, "ACTIVE");
        assertThrows(
                NullPointerException.class,
                () -> new UserRepository(new RecordingDatabaseController(nullRole))
                        .findById(3002)
        );
    }

    @Test
    public void invalidAndNullStatusPreserveEnumParsingFailures() {
        Map<String, Object> invalidStatus = userRow(3003, "STUDENT", "UNKNOWN");
        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> new UserRepository(new RecordingDatabaseController(invalidStatus))
                        .findByEmail("user3003@hsts.local")
        );
        assertEquals("No enum constant hsts.common.type.UserStatus.UNKNOWN",
                invalid.getMessage());

        Map<String, Object> nullStatus = userRow(3004, "STUDENT", null);
        assertThrows(
                NullPointerException.class,
                () -> new UserRepository(new RecordingDatabaseController(nullStatus))
                        .findByEmail("user3004@hsts.local")
        );
    }

    private static Map<String, Object> userRow() {
        Map<String, Object> row = userRow(1001, "STUDENT", "ACTIVE");
        row.put("full_name", "Development Student");
        row.put("email", "student@hsts.local");
        row.put("password_hash", "stored-password-hash");
        return row;
    }

    private static Map<String, Object> userRow(int userId, String role, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", userId);
        row.put("full_name", "Development User " + userId);
        row.put("email", "user" + userId + "@hsts.local");
        row.put("password_hash", "stored-password-hash-" + userId);
        row.put("role", role);
        row.put("status", status);
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
