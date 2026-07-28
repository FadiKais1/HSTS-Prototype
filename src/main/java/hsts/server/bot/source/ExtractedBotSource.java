package hsts.server.bot.source;

import hsts.common.type.BotSourceType;

import java.util.Objects;

public final class ExtractedBotSource {
    private final BotSourceType sourceType;
    private final String displayName;
    private final String extractedText;
    private final String contentSha256;

    public ExtractedBotSource(BotSourceType sourceType, String displayName,
                              String extractedText, String contentSha256) {
        this.sourceType = BotSourceExtractionSupport.requireExtractableType(sourceType);
        this.displayName = BotSourceExtractionSupport.requireSafeName(displayName);
        this.extractedText = BotSourceExtractionSupport.normalizeText(extractedText);
        this.contentSha256 = BotSourceExtractionSupport.requireChecksum(contentSha256);
        if (!Objects.equals(
                BotSourceExtractionSupport.sha256(this.extractedText),
                this.contentSha256
        )) {
            throw new IllegalArgumentException("Source checksum is invalid");
        }
    }

    public BotSourceType getSourceType() {
        return sourceType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public String getContentSha256() {
        return contentSha256;
    }
}
