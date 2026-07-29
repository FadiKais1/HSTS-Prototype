package hsts.common;

import hsts.common.type.QuestionIllustrationChange;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.Assert.*;

public class QuestionIllustrationContractTest {
    @Test
    public void mutationModesAreExactAndOrdered() {
        assertArrayEquals(new QuestionIllustrationChange[]{
                QuestionIllustrationChange.KEEP,
                QuestionIllustrationChange.REPLACE,
                QuestionIllustrationChange.REMOVE
        }, QuestionIllustrationChange.values());
    }

    @Test
    public void uploadPayloadDefensivelyCopiesBytesAndSerializes() throws Exception {
        byte[] source = {1, 2, 3};
        QuestionIllustrationUploadPayload payload =
                new QuestionIllustrationUploadPayload("safe.png", source);
        source[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, payload.getContent());
        byte[] returned = payload.getContent();
        returned[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, payload.getContent());
        QuestionIllustrationUploadPayload copy = roundTrip(payload);
        assertEquals("safe.png", copy.getFileName());
        assertArrayEquals(new byte[]{1, 2, 3}, copy.getContent());
        assertFalse(payload.toString().contains("[1, 2, 3]"));
    }

    @Test
    public void transportDefensivelyCopiesAndValidatesChecksum() throws Exception {
        byte[] bytes = {5, 6, 7};
        QuestionIllustrationDTO dto = new QuestionIllustrationDTO(
                "image/png", bytes, bytes.length, 2, 2, checksum(bytes)
        );
        bytes[0] = 1;
        assertArrayEquals(new byte[]{5, 6, 7}, dto.getContent());
        byte[] returned = dto.getContent();
        returned[0] = 1;
        assertArrayEquals(new byte[]{5, 6, 7}, dto.getContent());
        assertArrayEquals(dto.getContent(), roundTrip(dto).getContent());
        assertThrows(IllegalArgumentException.class, () ->
                new QuestionIllustrationDTO(
                        "image/png", new byte[]{1}, 1, 1, 1,
                        "0".repeat(64)
                ));
    }

    @Test
    public void transportRejectsUnsupportedOrUnboundedMetadata() {
        byte[] bytes = {1};
        String hash = checksum(bytes);
        assertThrows(IllegalArgumentException.class, () ->
                new QuestionIllustrationDTO("image/gif", bytes, 1, 1, 1, hash));
        assertThrows(IllegalArgumentException.class, () ->
                new QuestionIllustrationDTO(
                        "image/png", bytes, 1, 4097, 1, hash
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new QuestionIllustrationDTO(
                        "image/png", bytes, 1, 4001, 4001, hash
                ));
    }

    @Test
    public void updateMutationModeAndUploadMustBeConsistent() {
        QuestionIllustrationUploadPayload upload =
                new QuestionIllustrationUploadPayload("safe.png", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> update(null, null));
        assertThrows(IllegalArgumentException.class, () ->
                update(QuestionIllustrationChange.REPLACE, null));
        assertThrows(IllegalArgumentException.class, () ->
                update(QuestionIllustrationChange.KEEP, upload));
        assertThrows(IllegalArgumentException.class, () ->
                update(QuestionIllustrationChange.REMOVE, upload));
        assertEquals(QuestionIllustrationChange.REPLACE,
                update(QuestionIllustrationChange.REPLACE, upload)
                        .getIllustrationChange());
    }

    private static UpdateQuestionPayload update(
            QuestionIllustrationChange change,
            QuestionIllustrationUploadPayload upload
    ) {
        return new UpdateQuestionPayload(
                1, "Q", "T", "EASY", "ACTIVE", "",
                "A", "B", "C", "D", 1, 1, change, upload
        );
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())
        )) {
            return (T) input.readObject();
        }
    }
}
