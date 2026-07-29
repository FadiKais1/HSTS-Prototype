package hsts.server.entity;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

public final class QuestionIllustration {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_WIDTH = 4096;
    private static final int MAX_HEIGHT = 4096;
    private static final long MAX_PIXELS = 16_000_000L;

    private final String mediaType;
    private final byte[] content;
    private final int width;
    private final int height;
    private final String sha256;
    private final LocalDateTime createdAt;

    private QuestionIllustration(String mediaType, byte[] content,
                                 int width, int height, String sha256,
                                 LocalDateTime createdAt) {
        this.mediaType = mediaType;
        this.content = content.clone();
        this.width = width;
        this.height = height;
        this.sha256 = sha256;
        this.createdAt = Objects.requireNonNull(
                createdAt, "Question illustration timestamp is required"
        );
    }

    public static QuestionIllustration create(
            String fileName,
            byte[] content,
            LocalDateTime createdAt
    ) {
        fileName = validateFileName(fileName);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Question illustration content is required");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("Question illustration exceeds 2 MiB");
        }
        DecodedImage decoded = decode(content);
        String extensionType = extensionType(fileName);
        if (!decoded.mediaType().equals(extensionType)) {
            throw new IllegalArgumentException(
                    "Question illustration content does not match its type"
            );
        }
        return new QuestionIllustration(
                decoded.mediaType(), content, decoded.width(), decoded.height(),
                sha256(content), createdAt
        );
    }

    public static QuestionIllustration rehydrate(
            String mediaType, byte[] content, int byteLength,
            int width, int height, String sha256, LocalDateTime createdAt
    ) {
        validateMediaType(mediaType);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Question illustration content is required");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("Question illustration exceeds 2 MiB");
        }
        if (byteLength != content.length) {
            throw new IllegalArgumentException("Question illustration byte length is invalid");
        }
        validateDimensions(width, height);
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")
                || !MessageDigest.isEqual(
                        HexFormat.of().parseHex(sha256),
                        HexFormat.of().parseHex(sha256(content))
                )) {
            throw new IllegalArgumentException("Question illustration checksum is invalid");
        }
        DecodedImage decoded = decode(content);
        if (!mediaType.equals(decoded.mediaType())) {
            throw new IllegalArgumentException(
                    "Question illustration content does not match its type"
            );
        }
        if (width != decoded.width() || height != decoded.height()) {
            throw new IllegalArgumentException(
                    "Question illustration dimensions are invalid"
            );
        }
        return new QuestionIllustration(
                mediaType, content, width, height, sha256, createdAt
        );
    }

    public QuestionIllustration copy() {
        return new QuestionIllustration(
                mediaType, content, width, height, sha256, createdAt
        );
    }

    public String getMediaType() { return mediaType; }
    public byte[] getContent() { return content.clone(); }
    public int getByteLength() { return content.length; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getSha256() { return sha256; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    private static String validateFileName(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Question illustration file name is required"
            );
        }
        String fileName = value.trim();
        if (fileName.length() > 255 || fileName.equals(".") || fileName.equals("..")
                || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains(":") || fileName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    "Question illustration file name is invalid"
            );
        }
        extensionType(fileName);
        return fileName;
    }

    private static String extensionType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        throw new IllegalArgumentException("Question illustration type is invalid");
    }

    private static DecodedImage decode(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content)
        )) {
            if (input == null) {
                throw new IllegalArgumentException("Question illustration is malformed");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Question illustration type is invalid");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String mediaType = switch (format) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    default -> throw new IllegalArgumentException(
                            "Question illustration type is invalid"
                    );
                };
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width
                        || decoded.getHeight() != height) {
                    throw new IllegalArgumentException(
                            "Question illustration is malformed"
                    );
                }
                return new DecodedImage(mediaType, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Question illustration is malformed"
            );
        }
    }

    private static void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Question illustration dimensions are invalid"
            );
        }
        if (width > MAX_WIDTH || height > MAX_HEIGHT
                || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException(
                    "Question illustration exceeds dimension limits"
            );
        }
    }

    private static void validateMediaType(String mediaType) {
        if (!"image/png".equals(mediaType) && !"image/jpeg".equals(mediaType)) {
            throw new IllegalArgumentException("Question illustration type is invalid");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record DecodedImage(String mediaType, int width, int height) {
    }
}
