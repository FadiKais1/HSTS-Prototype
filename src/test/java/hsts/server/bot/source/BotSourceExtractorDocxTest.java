package hsts.server.bot.source;

import hsts.common.type.BotSourceType;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class BotSourceExtractorDocxTest {
    private final BotSourceExtractor extractor = new BotSourceExtractor();

    @Test
    public void extractsParagraphsAndTablesInDocumentOrder() throws Exception {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Before table");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("A1");
            table.getRow(0).getCell(1).setText("B1");
            table.getRow(1).getCell(0).setText("A2");
            table.getRow(1).getCell(1).setText("B2");
            document.createParagraph().createRun().setText("After table");
            document.write(output);
            content = output.toByteArray();
        }

        ExtractedBotSource result = extractor.extractFile(
                "lesson.docx", BotSourceType.DOCX, content
        );
        assertEquals(BotSourceType.DOCX, result.getSourceType());
        assertEquals("Before table\nA1\tB1\nA2\tB2\nAfter table",
                result.getExtractedText());
        assertEquals(BotSourceExtractionSupport.sha256(result.getExtractedText()),
                result.getContentSha256());
    }

    @Test
    public void rejectsMalformedAndArbitraryZipArchives() throws Exception {
        byte[] arbitraryZip = zipEntry(
                "ordinary.txt", "not Word".getBytes(StandardCharsets.UTF_8)
        );
        byte[] legacyDocument = ole2(false);
        assertMessage("Source document is malformed",
                () -> extractor.extractFile(
                        "broken.docx", BotSourceType.DOCX,
                        new byte[]{0x50, 0x4B, 0x03, 0x04, 1, 2, 3}
                ));
        assertMessage("Source file content does not match its type",
                () -> extractor.extractFile(
                        "archive.docx", BotSourceType.DOCX,
                        arbitraryZip
                ));
        assertMessage("Source file content does not match its type",
                () -> extractor.extractFile(
                        "legacy.docx", BotSourceType.DOCX,
                        legacyDocument
                ));
    }

    @Test
    public void rejectsEncryptedOoxmlContainer() throws Exception {
        byte[] encrypted = ole2(true);
        assertMessage("Source document is encrypted",
                () -> extractor.extractFile(
                        "secret.docx", BotSourceType.DOCX, encrypted
                ));
    }

    @Test
    public void rejectsEmptyDocxAndWrongExtension() throws Exception {
        byte[] empty = docxWithParagraph(null);
        byte[] content = docxWithParagraph("Text");
        assertMessage("Source text is empty",
                () -> extractor.extractFile("empty.docx", BotSourceType.DOCX, empty));
        assertMessage("Source file extension does not match its type",
                () -> extractor.extractFile("document.pdf", BotSourceType.DOCX,
                        content));
    }

    @Test
    public void rejectsSafelyBoundedExtremeZipExpansion() throws Exception {
        byte[] expanded = new byte[10 * 1024 * 1024 + 1];
        byte[] archive;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, "[Content_Types].xml", "types".getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "word/document.xml", expanded);
            archive = output.toByteArray();
        }
        assertTrue("Synthetic archive must remain below upload limit",
                archive.length <= BotSourceExtractor.MAX_FILE_BYTES);
        assertMessage("Source document is malformed",
                () -> extractor.extractFile("expanded.docx", BotSourceType.DOCX, archive));
    }

    @Test
    public void callerMutationCannotChangeExtractedDocxResult() throws Exception {
        byte[] content = docxWithParagraph("Stable content");
        ExtractedBotSource result = extractor.extractFile(
                "stable.docx", BotSourceType.DOCX, content
        );
        content[0] = 0;
        assertEquals("Stable content", result.getExtractedText());
    }

    private static byte[] docxWithParagraph(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (text != null) {
                document.createParagraph().createRun().setText(text);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] zipEntry(String name, byte[] content) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, name, content);
            return output.toByteArray();
        }
    }

    private static void addEntry(ZipOutputStream zip, String name, byte[] content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static byte[] ole2(boolean encryptedMarkers) throws IOException {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (encryptedMarkers) {
                fileSystem.getRoot().createDocument(
                        "EncryptionInfo",
                        new ByteArrayInputStream(new byte[]{1, 2, 3})
                );
                fileSystem.getRoot().createDocument(
                        "EncryptedPackage",
                        new ByteArrayInputStream(new byte[]{4, 5, 6})
                );
            } else {
                fileSystem.getRoot().createDocument(
                        "WordDocument",
                        new ByteArrayInputStream(new byte[]{1, 2, 3})
                );
            }
            fileSystem.writeFilesystem(output);
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
