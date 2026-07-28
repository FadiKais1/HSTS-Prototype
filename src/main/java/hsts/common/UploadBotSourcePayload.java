package hsts.common;

import hsts.common.type.BotSourceType;

import java.io.Serializable;
import java.util.Arrays;

public final class UploadBotSourcePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final String fileName;
    private final BotSourceType sourceType;
    private final byte[] content;

    public UploadBotSourcePayload(int botId, String fileName,
                                  BotSourceType sourceType, byte[] content) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        this.fileName = requireSafeFileName(fileName);
        if (sourceType != BotSourceType.TXT
                && sourceType != BotSourceType.PDF
                && sourceType != BotSourceType.DOCX) {
            throw new IllegalArgumentException("Upload source type must be TXT, PDF, or DOCX");
        }
        this.sourceType = sourceType;
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Upload content is required");
        }
        this.content = Arrays.copyOf(content, content.length);
    }

    public int getBotId() { return botId; }
    public String getFileName() { return fileName; }
    public BotSourceType getSourceType() { return sourceType; }
    public byte[] getContent() { return Arrays.copyOf(content, content.length); }

    private static String requireSafeFileName(String fileName) {
        String normalized = BotContractSupport.requireNonBlank(
                fileName, "Upload file name is required"
        );
        if (normalized.equals(".") || normalized.equals("..")
                || normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("Upload file name must be a safe basename");
        }
        return normalized;
    }
}
