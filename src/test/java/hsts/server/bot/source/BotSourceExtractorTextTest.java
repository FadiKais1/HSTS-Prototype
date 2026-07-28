package hsts.server.bot.source;

import hsts.common.type.BotSourceType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class BotSourceExtractorTextTest {
    private final BotSourceExtractor extractor = new BotSourceExtractor();

    @Test
    public void freeTextMapsFieldsNormalizesAndProducesStableChecksum() {
        ExtractedBotSource result = extractor.extractFreeText(
                "  Course notes  ",
                "\uFEFF  Alpha  \r\nBeta\t \rGamma  "
        );

        assertEquals(BotSourceType.FREE_TEXT, result.getSourceType());
        assertEquals("Course notes", result.getDisplayName());
        assertEquals("Alpha\nBeta\nGamma", result.getExtractedText());
        assertEquals(64, result.getContentSha256().length());
        assertEquals(result.getContentSha256(),
                extractor.extractFreeText("Another label", "Alpha\nBeta\nGamma")
                        .getContentSha256());
    }

    @Test
    public void freeTextEnforcesBlankNulAndCharacterLimits() {
        assertMessage("Source text is empty",
                () -> extractor.extractFreeText("Notes", " \r\n\t "));
        assertMessage("Source text contains NUL characters",
                () -> extractor.extractFreeText("Notes", "A\0B"));

        String atLimit = "a".repeat(BotSourceExtractor.MAX_EXTRACTED_CHARACTERS);
        assertEquals(atLimit.length(),
                extractor.extractFreeText("Notes", atLimit).getExtractedText().length());
        assertMessage("Extracted source text exceeds 500000 characters",
                () -> extractor.extractFreeText("Notes", atLimit + "a"));
    }

    @Test
    public void checksumUsesNormalizedUtf8TextOnly() {
        ExtractedBotSource result = extractor.extractFreeText("First", "hello");
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                result.getContentSha256()
        );
        assertEquals(result.getContentSha256(),
                extractor.extractFreeText("Second", "\uFEFFhello\r\n")
                        .getContentSha256());
    }

    @Test
    public void namesAndFileInputsUseStableValidation() {
        assertMessage("Source file name is required",
                () -> extractor.extractFreeText(" ", "text"));
        for (String name : new String[]{"../notes", "folder/notes", "folder\\notes",
                ".", "..", "bad\0name"}) {
            assertMessage("Source file name is invalid",
                    () -> extractor.extractFreeText(name, "text"));
        }

        assertMessage("Source file content is required",
                () -> extractor.extractFile("notes.txt", BotSourceType.TXT, null));
        assertMessage("Source file content is required",
                () -> extractor.extractFile("notes.txt", BotSourceType.TXT, new byte[0]));
        assertMessage("Source file exceeds 5 MiB",
                () -> extractor.extractFile(
                        "notes.txt", BotSourceType.TXT,
                        new byte[BotSourceExtractor.MAX_FILE_BYTES + 1]
                ));
        assertMessage("Source file type is invalid",
                () -> extractor.extractFile("notes.txt", BotSourceType.FREE_TEXT,
                        "text".getBytes(StandardCharsets.UTF_8)));
        assertMessage("Source file type is invalid",
                () -> extractor.extractFile("notes.txt", BotSourceType.QUESTION_BANK,
                        "text".getBytes(StandardCharsets.UTF_8)));
        assertMessage("Source file type is invalid",
                () -> extractor.extractFile("notes.txt", null,
                        "text".getBytes(StandardCharsets.UTF_8)));
        assertMessage("Source file extension does not match its type",
                () -> extractor.extractFile("notes.pdf", BotSourceType.TXT,
                        "text".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void txtUsesStrictUtf8BomAndLineNormalization() {
        byte[] text = "\uFEFFFirst\r\nSecond  \rThird\t "
                .getBytes(StandardCharsets.UTF_8);
        ExtractedBotSource result = extractor.extractFile(
                "NOTES.TXT", BotSourceType.TXT, text
        );

        assertEquals(BotSourceType.TXT, result.getSourceType());
        assertEquals("NOTES.TXT", result.getDisplayName());
        assertEquals("First\nSecond\nThird", result.getExtractedText());
        assertEquals(
                extractor.extractFreeText("Label", "First\nSecond\nThird")
                        .getContentSha256(),
                result.getContentSha256()
        );
    }

    @Test
    public void txtRejectsInvalidUtf8BinaryMagicNulAndEmptyText() {
        assertMessage("Source TXT must be valid UTF-8 text",
                () -> extractor.extractFile("bad.txt", BotSourceType.TXT,
                        new byte[]{(byte) 0xC3, 0x28}));
        assertMessage("Source TXT must be valid UTF-8 text",
                () -> extractor.extractFile("bad.txt", BotSourceType.TXT,
                        new byte[]{'a', 0, 'b'}));
        assertMessage("Source file content does not match its type",
                () -> extractor.extractFile("bad.txt", BotSourceType.TXT,
                        "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertMessage("Source file content does not match its type",
                () -> extractor.extractFile("bad.txt", BotSourceType.TXT,
                        new byte[]{0x50, 0x4B, 0x03, 0x04, 1}));
        assertMessage("Source text is empty",
                () -> extractor.extractFile("empty.txt", BotSourceType.TXT,
                        " \r\n ".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void extractorSnapshotsCallerBytesAndRetainsNoRawContent() {
        byte[] supplied = "original".getBytes(StandardCharsets.UTF_8);
        byte[] originalCopy = supplied.clone();
        ExtractedBotSource result = extractor.extractFile(
                "notes.txt", BotSourceType.TXT, supplied
        );
        Arrays.fill(supplied, (byte) 'x');

        assertEquals("original", result.getExtractedText());
        assertNotEquals(new String(supplied, StandardCharsets.UTF_8),
                result.getExtractedText());
        assertEquals("original", new String(originalCopy, StandardCharsets.UTF_8));
    }

    private static void assertMessage(String expected, Runnable operation) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, operation::run
        );
        assertEquals(expected, error.getMessage());
    }
}
