package hsts.common;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LoginContractTest {
    @Test
    public void loginRequestPayloadMapsAndSerializes() throws Exception {
        LoginRequestPayload original = new LoginRequestPayload(
                "student@hsts.local",
                "development-password"
        );

        LoginRequestPayload restored = roundTrip(original);

        assertEquals("student@hsts.local", restored.getEmail());
        assertEquals("development-password", restored.getPassword());
    }

    @Test
    public void loginResultMapsAndSerializesWithoutPasswordData() throws Exception {
        LoginResult original = new LoginResult(
                1001,
                "Development Student",
                UserRole.STUDENT,
                UserStatus.ACTIVE,
                "session-1001"
        );

        LoginResult restored = roundTrip(original);

        assertEquals(1001, restored.getUserId());
        assertEquals("Development Student", restored.getFullName());
        assertEquals(UserRole.STUDENT, restored.getRole());
        assertEquals(UserStatus.ACTIVE, restored.getStatus());
        assertEquals("session-1001", restored.getSessionId());

        for (Field field : LoginResult.class.getDeclaredFields()) {
            String fieldName = field.getName().toLowerCase();
            assertFalse(fieldName.contains("password"));
            assertFalse(fieldName.contains("passwordhash"));
        }
        assertFalse(Arrays.stream(LoginResult.class.getMethods())
                .map(method -> method.getName().toLowerCase())
                .anyMatch(methodName -> methodName.contains("password")));
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
