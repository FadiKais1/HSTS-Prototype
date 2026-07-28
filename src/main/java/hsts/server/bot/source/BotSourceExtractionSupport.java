package hsts.server.bot.source;

import hsts.common.type.BotSourceType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

final class BotSourceExtractionSupport {
    static final int MAX_EXTRACTED_CHARACTERS = 500_000;

    private BotSourceExtractionSupport() {
    }

    static BotSourceType requireExtractableType(BotSourceType sourceType) {
        if (sourceType != BotSourceType.QUESTION_BANK
                && sourceType != BotSourceType.FREE_TEXT
                && sourceType != BotSourceType.TXT
                && sourceType != BotSourceType.PDF
                && sourceType != BotSourceType.DOCX) {
            throw new IllegalArgumentException("Source file type is invalid");
        }
        return sourceType;
    }

    static String requireSafeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Source file name is required");
        }
        String normalized = name.trim();
        if (normalized.equals(".") || normalized.equals("..")
                || normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0
                || normalized.codePoints().anyMatch(BotSourceExtractionSupport::isControl)) {
            throw new IllegalArgumentException("Source file name is invalid");
        }
        return normalized;
    }

    static String normalizeText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Source text is empty");
        }
        String normalized = text;
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        if (normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Source text contains NUL characters");
        }
        normalized = normalized.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\f', '\n');

        String[] lines = normalized.split("\n", -1);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                result.append('\n');
            }
            result.append(lines[index].stripTrailing());
        }

        normalized = result.toString().strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Source text is empty");
        }
        if (normalized.length() > MAX_EXTRACTED_CHARACTERS) {
            throw new IllegalArgumentException(
                    "Extracted source text exceeds 500000 characters"
            );
        }
        return normalized;
    }

    static String sha256(String normalizedText) {
        Objects.requireNonNull(normalizedText, "normalizedText");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    static String requireChecksum(String checksum) {
        if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Source checksum is invalid");
        }
        return checksum;
    }

    private static boolean isControl(int codePoint) {
        return Character.isISOControl(codePoint);
    }
}
