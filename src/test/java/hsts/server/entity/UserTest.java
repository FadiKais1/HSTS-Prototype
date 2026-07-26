package hsts.server.entity;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserTest {
    @Test
    public void constructorMapsAllUmlAttributesAndActiveStatus() {
        User user = new User(
                42,
                "Development Teacher",
                "teacher@hsts.local",
                "encoded-password-hash",
                UserRole.TEACHER,
                UserStatus.ACTIVE
        );

        assertEquals(42, user.getUserId());
        assertEquals("Development Teacher", user.getFullName());
        assertEquals("teacher@hsts.local", user.getEmail());
        assertEquals("encoded-password-hash", user.getPasswordHash());
        assertEquals(UserRole.TEACHER, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.isActive());
    }

    @Test
    public void blockedUserIsNotActive() {
        User user = new User(
                43,
                "Development Student",
                "student@hsts.local",
                "encoded-password-hash",
                UserRole.STUDENT,
                UserStatus.BLOCKED
        );

        assertFalse(user.isActive());
    }
}
