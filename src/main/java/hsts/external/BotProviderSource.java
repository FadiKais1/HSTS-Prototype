package hsts.external;

import hsts.common.type.BotSourceType;

public final class BotProviderSource {
    private static final int MAX_TEXT_LENGTH = 500_000;

    private final String displayName;
    private final BotSourceType sourceType;
    private final String extractedText;

    public BotProviderSource(String displayName, BotSourceType sourceType,
                             String extractedText) {
        this.displayName = requireText(displayName, 255, "Source display name is required");
        if (sourceType == null) {
            throw new IllegalArgumentException("Bot source type is required");
        }
        this.sourceType = sourceType;
        this.extractedText = requireText(
                extractedText, MAX_TEXT_LENGTH, "Extracted source text is required"
        );
    }

    public String getDisplayName() { return displayName; }
    public BotSourceType getSourceType() { return sourceType; }
    public String getExtractedText() { return extractedText; }

    private static String requireText(String value, int maximum, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException("Bot provider text is too long");
        }
        return normalized;
    }
}
