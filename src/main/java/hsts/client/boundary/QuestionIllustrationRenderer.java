package hsts.client.boundary;

import hsts.common.QuestionIllustrationDTO;
import hsts.common.QuestionIllustrationUploadPayload;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class QuestionIllustrationRenderer {
    public static final String UNAVAILABLE_MESSAGE =
            "Question illustration is unavailable";

    private final ImageView imageView;
    private final Label errorLabel;
    private final Node container;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean disposed;

    public QuestionIllustrationRenderer(ImageView imageView, Label errorLabel,
                                        Node container) {
        this.imageView = imageView;
        this.errorLabel = errorLabel;
        this.container = container;
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(520);
        imageView.setFitHeight(320);
        clear(false);
    }

    public void render(QuestionIllustrationDTO illustration) {
        long token = generation.incrementAndGet();
        clear(false);
        if (illustration == null || disposed) {
            return;
        }

        CompletableFuture.supplyAsync(() -> decode(illustration))
                .whenComplete((image, failure) -> Platform.runLater(() -> {
                    if (disposed || generation.get() != token) {
                        return;
                    }
                    if (failure != null || image == null || image.isError()) {
                        clear(true);
                        return;
                    }
                    imageView.setImage(image);
                    imageView.setVisible(true);
                    imageView.setManaged(true);
                    errorLabel.setText("");
                    errorLabel.setVisible(false);
                    errorLabel.setManaged(false);
                    container.setVisible(true);
                    container.setManaged(true);
                }));
    }

    public void renderUpload(QuestionIllustrationUploadPayload upload) {
        long token = generation.incrementAndGet();
        clear(false);
        if (upload == null || disposed) return;
        CompletableFuture.supplyAsync(() -> previewDto(upload))
                .whenComplete((preview, failure) -> Platform.runLater(() -> {
                    if (disposed || generation.get() != token) return;
                    if (failure != null) clear(true);
                    else render(preview);
                }));
    }

    public void dispose() {
        disposed = true;
        generation.incrementAndGet();
        clear(false);
    }

    private void clear(boolean invalid) {
        imageView.setImage(null);
        imageView.setVisible(false);
        imageView.setManaged(false);
        errorLabel.setText(invalid ? UNAVAILABLE_MESSAGE : "");
        errorLabel.setVisible(invalid);
        errorLabel.setManaged(invalid);
        container.setVisible(invalid);
        container.setManaged(invalid);
    }

    static Image decode(QuestionIllustrationDTO illustration) {
        String mediaType = illustration.getMediaType();
        byte[] bytes = illustration.getContent();
        if (!("image/png".equals(mediaType) || "image/jpeg".equals(mediaType))
                || bytes == null || bytes.length == 0
                || bytes.length != illustration.getByteLength()
                || bytes.length > QuestionIllustrationDTO.MAX_BYTES
                || illustration.getWidth() <= 0
                || illustration.getHeight() <= 0
                || illustration.getWidth() > QuestionIllustrationDTO.MAX_WIDTH
                || illustration.getHeight() > QuestionIllustrationDTO.MAX_HEIGHT
                || (long) illustration.getWidth() * illustration.getHeight()
                > QuestionIllustrationDTO.MAX_PIXELS
                || !checksum(bytes).equals(illustration.getSha256())) {
            throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
        }
        try {
            var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null
                    || decoded.getWidth() != illustration.getWidth()
                    || decoded.getHeight() != illustration.getHeight()) {
                throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
            }
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
        }
    }

    private static QuestionIllustrationDTO previewDto(
            QuestionIllustrationUploadPayload upload
    ) {
        byte[] bytes = upload.getContent();
        if (bytes == null || bytes.length == 0
                || bytes.length > QuestionIllustrationDTO.MAX_BYTES) {
            throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
        }
        try {
            var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) throw new IllegalArgumentException();
            String lowerName = upload.getFileName().toLowerCase(Locale.ROOT);
            String mediaType = lowerName.endsWith(".png")
                    ? "image/png"
                    : lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                    ? "image/jpeg" : null;
            if (mediaType == null) throw new IllegalArgumentException();
            return new QuestionIllustrationDTO(
                    mediaType, bytes, bytes.length, decoded.getWidth(),
                    decoded.getHeight(), checksum(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
        }
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
