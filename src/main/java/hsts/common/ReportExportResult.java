package hsts.common;

import java.io.Serializable;
import java.util.Arrays;

public final class ReportExportResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String suggestedFilename;
    private final String mediaType;
    private final byte[] bytes;

    public ReportExportResult(String suggestedFilename, String mediaType, byte[] bytes) {
        if (suggestedFilename == null || suggestedFilename.isBlank()) {
            throw new IllegalArgumentException("Export filename is required");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("Export media type is required");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Export data is required");
        }
        this.suggestedFilename = suggestedFilename;
        this.mediaType = mediaType;
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public String getSuggestedFilename() { return suggestedFilename; }
    public String getMediaType() { return mediaType; }
    public byte[] getBytes() { return Arrays.copyOf(bytes, bytes.length); }
}
