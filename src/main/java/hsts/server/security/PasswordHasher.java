package hsts.server.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null");
        }

        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derivedKey = derive(password, salt, ITERATIONS, KEY_BYTES);

        return PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derivedKey);
    }

    public static boolean matches(String password, String encodedHash) {
        if (password == null || encodedHash == null) {
            return false;
        }

        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        final int iterations;
        final byte[] salt;
        final byte[] expectedHash;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expectedHash = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        if (iterations != ITERATIONS || salt.length != SALT_BYTES
                || expectedHash.length != KEY_BYTES) {
            return false;
        }

        byte[] actualHash = derive(password, salt, iterations, expectedHash.length);
        return MessageDigest.isEqual(expectedHash, actualHash);
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyBytes) {
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt,
                iterations, keyBytes * Byte.SIZE);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(keySpec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Password hashing algorithm is unavailable", exception);
        } finally {
            keySpec.clearPassword();
        }
    }
}
