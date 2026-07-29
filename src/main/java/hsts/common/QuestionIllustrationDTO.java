package hsts.common;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class QuestionIllustrationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_WIDTH = 4096;
    public static final int MAX_HEIGHT = 4096;
    public static final long MAX_PIXELS = 16_000_000L;

    private final String mediaType;
    private final byte[] content;
    private final int byteLength;
    private final int width;
    private final int height;
    private final String sha256;

    public QuestionIllustrationDTO(String mediaType, byte[] content,
                                   int byteLength, int width, int height,
                                   String sha256) {
        if (!"image/png".equals(mediaType) && !"image/jpeg".equals(mediaType)) {
            throw new IllegalArgumentException("Question illustration type is invalid");
        }
        byte[] safeContent = Objects.requireNonNull(
                content, "Question illustration content is required"
        ).clone();
        if (safeContent.length == 0) {
            throw new IllegalArgumentException("Question illustration content is required");
        }
        if (safeContent.length > MAX_BYTES) {
            throw new IllegalArgumentException("Question illustration exceeds 2 MiB");
        }
        if (byteLength != safeContent.length) {
            throw new IllegalArgumentException("Question illustration byte length is invalid");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Question illustration dimensions are invalid");
        }
        if (width > MAX_WIDTH || height > MAX_HEIGHT
                || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException(
                    "Question illustration exceeds dimension limits"
            );
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")
                || !MessageDigest.isEqual(
                        HexFormat.of().parseHex(sha256), digest(safeContent)
                )) {
            throw new IllegalArgumentException("Question illustration checksum is invalid");
        }
        this.mediaType = mediaType;
        this.content = safeContent;
        this.byteLength = byteLength;
        this.width = width;
        this.height = height;
        this.sha256 = sha256;
    }

    public String getMediaType() { return mediaType; }
    public byte[] getContent() { return content.clone(); }
    public int getByteLength() { return byteLength; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getSha256() { return sha256; }

    private static byte[] digest(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
