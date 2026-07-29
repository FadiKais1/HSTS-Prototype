package hsts.common;

import java.io.Serializable;
public final class QuestionIllustrationUploadPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final byte[] content;

    public QuestionIllustrationUploadPayload(String fileName, byte[] content) {
        this.fileName = fileName;
        this.content = content == null ? null : content.clone();
    }

    public String getFileName() { return fileName; }

    public byte[] getContent() {
        return content == null ? null : content.clone();
    }
}
