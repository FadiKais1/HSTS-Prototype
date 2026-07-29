package hsts.server.entity;

import hsts.common.QuestionIllustrationUploadPayload;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

public class QuestionIllustrationTest {
    @Test
    public void validPngAndJpegAreDecodedAndNormalized() throws Exception {
        QuestionIllustration png = create("diagram.png", image("png"));
        QuestionIllustration jpeg = create("photo.JPEG", image("jpeg"));
        assertEquals("image/png", png.getMediaType());
        assertEquals("image/jpeg", jpeg.getMediaType());
        assertEquals(3, png.getWidth());
        assertEquals(2, png.getHeight());
        assertEquals(64, png.getSha256().length());
    }

    @Test
    public void callerAndReturnedArraysCannotMutateDomainValue() throws Exception {
        byte[] bytes = image("png");
        QuestionIllustration illustration = create("safe.png", bytes);
        bytes[0] ^= 1;
        byte[] first = illustration.getContent();
        first[0] ^= 1;
        assertArrayEquals(illustration.getContent(), illustration.copy().getContent());
        assertArrayEquals(illustration.getContent(), illustration.toDto().getContent());
    }

    @Test
    public void unsafeNamesAndUnsupportedTypesAreRejected() throws Exception {
        byte[] png = image("png");
        assertMessage("Question illustration file name is required", () ->
                create(" ", png));
        assertMessage("Question illustration file name is invalid", () ->
                create("../safe.png", png));
        assertMessage("Question illustration type is invalid", () ->
                create("safe.gif", png));
    }

    @Test
    public void extensionMismatchAndMalformedContentAreRejected() throws Exception {
        byte[] png = image("png");
        assertMessage("Question illustration content does not match its type", () ->
                create("wrong.jpg", png));
        IllegalArgumentException malformed = assertThrows(
                IllegalArgumentException.class,
                () -> create("fake.png", new byte[]{1, 2, 3})
        );
        assertTrue(malformed.getMessage().startsWith("Question illustration"));
    }

    @Test
    public void emptyAndOversizedContentUseStableErrors() {
        assertMessage("Question illustration content is required", () ->
                create("empty.png", new byte[0]));
        assertMessage("Question illustration exceeds 2 MiB", () ->
                create("large.png", new byte[2 * 1024 * 1024 + 1]));
    }

    @Test
    public void persistedChecksumOrDimensionsCannotBeForged() throws Exception {
        QuestionIllustration valid = create("safe.png", image("png"));
        assertThrows(IllegalArgumentException.class, () ->
                QuestionIllustration.rehydrate(
                        valid.getMediaType(), valid.getContent(), valid.getByteLength(),
                        valid.getWidth(), valid.getHeight(), "0".repeat(64),
                        valid.getCreatedAt()
                ));
        assertThrows(IllegalArgumentException.class, () ->
                QuestionIllustration.rehydrate(
                        valid.getMediaType(), valid.getContent(), valid.getByteLength(),
                        valid.getWidth() + 1, valid.getHeight(), valid.getSha256(),
                        valid.getCreatedAt()
                ));
    }

    @Test
    public void serverValueIsNotSerializableAndContainsNoPathOrUrlState() {
        assertFalse(Serializable.class.isAssignableFrom(QuestionIllustration.class));
        var names = java.util.Arrays.stream(QuestionIllustration.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toList();
        assertFalse(names.contains("path"));
        assertFalse(names.contains("url"));
    }

    private static QuestionIllustration create(String name, byte[] bytes) {
        return QuestionIllustration.create(
                new QuestionIllustrationUploadPayload(name, bytes),
                LocalDateTime.of(2026, 7, 29, 12, 0)
        );
    }

    private static byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(
                3, 2, "jpeg".equals(format)
                ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB
        );
        image.setRGB(1, 1, 0xff336699);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, bytes));
        return bytes.toByteArray();
    }

    private static void assertMessage(String expected, Runnable action) {
        assertEquals(expected, assertThrows(
                IllegalArgumentException.class, action::run
        ).getMessage());
    }
}
