package hsts.server.bot.source;

import hsts.common.type.BotSourceType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class BotSourceExtractorPdfTest {
    private final BotSourceExtractor extractor = new BotSourceExtractor();

    @Test
    public void extractsGeneratedPdfPagesInOrderAndChecksumsNormalizedText()
            throws Exception {
        byte[] content = pdf("First page", "Second page");
        ExtractedBotSource result = extractor.extractFile(
                "lesson.PDF", BotSourceType.PDF, content
        );

        assertEquals(BotSourceType.PDF, result.getSourceType());
        assertTrue(result.getExtractedText().indexOf("First page")
                < result.getExtractedText().indexOf("Second page"));
        assertEquals(
                BotSourceExtractionSupport.sha256(result.getExtractedText()),
                result.getContentSha256()
        );
    }

    @Test
    public void rejectsMoreThanThreeHundredPages() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int page = 0; page < 301; page++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            assertMessage("Source PDF exceeds 300 pages",
                    () -> extractor.extractFile(
                            "large.pdf", BotSourceType.PDF, output.toByteArray()
                    ));
        }
    }

    @Test
    public void rejectsEncryptedPdf() throws Exception {
        byte[] encrypted;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password", "user-password", new AccessPermission()
            );
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            encrypted = output.toByteArray();
        }

        assertMessage("Source document is encrypted",
                () -> extractor.extractFile("secret.pdf", BotSourceType.PDF, encrypted));
    }

    @Test
    public void rejectsMalformedFakeAndEmptyTextPdf() throws Exception {
        assertMessage("Source document is malformed",
                () -> extractor.extractFile(
                        "fake.pdf", BotSourceType.PDF,
                        "%PDF-not-a-document".getBytes(StandardCharsets.US_ASCII)
                ));
        assertMessage("Source file content does not match its type",
                () -> extractor.extractFile(
                        "fake.pdf", BotSourceType.PDF,
                        "not a pdf".getBytes(StandardCharsets.UTF_8)
                ));

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            assertMessage("Source text is empty",
                    () -> extractor.extractFile(
                            "empty.pdf", BotSourceType.PDF, output.toByteArray()
                    ));
        }
    }

    @Test
    public void rejectsWrongPdfExtension() throws Exception {
        byte[] content = pdf("Text");
        assertMessage("Source file extension does not match its type",
                () -> extractor.extractFile("lesson.txt", BotSourceType.PDF,
                        content));
    }

    private static byte[] pdf(String... pageTexts) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(pageText);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void assertMessage(String expected, Runnable operation) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, operation::run
        );
        assertEquals(expected, error.getMessage());
    }
}
