package hsts.server.security;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {
    @Test
    public void samePasswordUsesDifferentRandomSalts() {
        String firstHash = PasswordHasher.hash("correct horse battery staple");
        String secondHash = PasswordHasher.hash("correct horse battery staple");

        assertNotEquals(firstHash, secondHash);
        assertTrue(firstHash.startsWith("pbkdf2-sha256$210000$"));
        assertTrue(secondHash.startsWith("pbkdf2-sha256$210000$"));
    }

    @Test
    public void correctPasswordMatchesAndWrongPasswordFails() {
        String encodedHash = PasswordHasher.hash("development-password");

        assertTrue(PasswordHasher.matches("development-password", encodedHash));
        assertFalse(PasswordHasher.matches("wrong-password", encodedHash));
    }

    @Test
    public void hashRejectsNullPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }

    @Test
    public void malformedAndNullHashesFailSafely() {
        String validHash = PasswordHasher.hash("development-password");
        String[] parts = validHash.split("\\$");

        assertFalse(PasswordHasher.matches(null, validHash));
        assertFalse(PasswordHasher.matches("development-password", null));
        assertFalse(PasswordHasher.matches("development-password", ""));
        assertFalse(PasswordHasher.matches("development-password", "not-a-hash"));
        assertFalse(PasswordHasher.matches(
                "development-password",
                "unsupported$210000$" + parts[2] + "$" + parts[3]
        ));
        assertFalse(PasswordHasher.matches(
                "development-password",
                "pbkdf2-sha256$not-a-number$" + parts[2] + "$" + parts[3]
        ));
        assertFalse(PasswordHasher.matches(
                "development-password",
                "pbkdf2-sha256$0$" + parts[2] + "$" + parts[3]
        ));
        assertFalse(PasswordHasher.matches(
                "development-password",
                "pbkdf2-sha256$210000$invalid-base64!$" + parts[3]
        ));
        assertFalse(PasswordHasher.matches(
                "development-password",
                "pbkdf2-sha256$210000$" + parts[2] + "$invalid-base64!"
        ));
    }
}
