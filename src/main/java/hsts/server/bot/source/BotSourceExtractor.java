package hsts.server.bot.source;

import hsts.common.type.BotSourceType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BotSourceExtractor {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_EXTRACTED_CHARACTERS =
            BotSourceExtractionSupport.MAX_EXTRACTED_CHARACTERS;
    public static final int MAX_PDF_PAGES = 300;

    private static final int MAX_DOCX_ENTRIES = 10_000;
    private static final long MAX_DOCX_ENTRY_BYTES = 10L * 1024 * 1024;
    private static final long MAX_DOCX_EXPANDED_BYTES = 50L * 1024 * 1024;
    private static final long MAX_DOCX_INFLATE_RATIO = 100L;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    public ExtractedBotSource extractFreeText(String displayName, String text) {
        String safeName = BotSourceExtractionSupport.requireSafeName(displayName);
        String normalizedText = BotSourceExtractionSupport.normalizeText(text);
        return result(BotSourceType.FREE_TEXT, safeName, normalizedText);
    }

    public ExtractedBotSource extractFile(String fileName, BotSourceType sourceType,
                                          byte[] content) {
        String safeName = BotSourceExtractionSupport.requireSafeName(fileName);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Source file content is required");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Source file exceeds 5 MiB");
        }
        BotSourceType validatedType = validateFileType(sourceType);
        validateExtension(safeName, validatedType);

        byte[] snapshot = content.clone();
        String extractedText = switch (validatedType) {
            case TXT -> extractTxt(snapshot);
            case PDF -> extractPdf(snapshot);
            case DOCX -> extractDocx(snapshot);
            default -> throw new IllegalArgumentException("Source file type is invalid");
        };
        String normalizedText = BotSourceExtractionSupport.normalizeText(extractedText);
        return result(validatedType, safeName, normalizedText);
    }

    private static ExtractedBotSource result(BotSourceType sourceType,
                                             String displayName,
                                             String normalizedText) {
        return new ExtractedBotSource(
                sourceType,
                displayName,
                normalizedText,
                BotSourceExtractionSupport.sha256(normalizedText)
        );
    }

    private static BotSourceType validateFileType(BotSourceType sourceType) {
        if (sourceType != BotSourceType.TXT
                && sourceType != BotSourceType.PDF
                && sourceType != BotSourceType.DOCX) {
            throw new IllegalArgumentException("Source file type is invalid");
        }
        return sourceType;
    }

    private static void validateExtension(String fileName, BotSourceType sourceType) {
        String expectedExtension = "." + sourceType.name().toLowerCase(Locale.ROOT);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(expectedExtension)) {
            throw new IllegalArgumentException(
                    "Source file extension does not match its type"
            );
        }
    }

    private static String extractTxt(byte[] content) {
        if (hasPdfHeader(content) || startsWith(content, ZIP_MAGIC)
                || startsWith(content, OLE2_MAGIC)) {
            throw new IllegalArgumentException(
                    "Source file content does not match its type"
            );
        }
        for (byte value : content) {
            if (value == 0) {
                throw new IllegalArgumentException(
                        "Source TXT must be valid UTF-8 text"
                );
            }
        }

        String decoded;
        try {
            CharBuffer characters = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            decoded = characters.toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(
                    "Source TXT must be valid UTF-8 text", e
            );
        }

        int disallowedControls = 0;
        for (int index = 0; index < decoded.length(); index++) {
            char value = decoded.charAt(index);
            if (Character.isISOControl(value)
                    && value != '\t' && value != '\n' && value != '\r'
                    && value != '\f' && value != '\uFEFF') {
                disallowedControls++;
            }
        }
        int allowedControls = Math.max(8, decoded.length() / 100);
        if (disallowedControls > allowedControls) {
            throw new IllegalArgumentException(
                    "Source TXT must be valid UTF-8 text"
            );
        }
        return decoded;
    }

    private static String extractPdf(byte[] content) {
        if (!hasPdfHeader(content)) {
            throw new IllegalArgumentException(
                    "Source file content does not match its type"
            );
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Source document is encrypted");
            }
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new IllegalArgumentException("Source PDF exceeds 300 pages");
            }
            return new PDFTextStripper().getText(document);
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Source document is encrypted", e);
        } catch (IllegalArgumentException e) {
            if (isStableExtractionFailure(e)) {
                throw e;
            }
            throw new IllegalArgumentException("Source document is malformed", e);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Source document is malformed", e);
        }
    }

    private static String extractDocx(byte[] content) {
        if (startsWith(content, OLE2_MAGIC)) {
            if (isEncryptedOoxml(content)) {
                throw new IllegalArgumentException("Source document is encrypted");
            }
            throw new IllegalArgumentException(
                    "Source file content does not match its type"
            );
        }
        if (!startsWith(content, ZIP_MAGIC)) {
            throw new IllegalArgumentException(
                    "Source file content does not match its type"
            );
        }

        validateDocxArchive(content);
        try (ByteArrayInputStream input = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(input)) {
            return extractDocxBody(document);
        } catch (EncryptedDocumentException e) {
            throw new IllegalArgumentException("Source document is encrypted", e);
        } catch (ExtractedTextLimitException e) {
            throw new IllegalArgumentException(
                    "Extracted source text exceeds 500000 characters", e
            );
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Source document is malformed", e);
        }
    }

    private static boolean isEncryptedOoxml(byte[] content) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content);
             POIFSFileSystem fileSystem = new POIFSFileSystem(input)) {
            return fileSystem.getRoot().hasEntry("EncryptionInfo")
                    && fileSystem.getRoot().hasEntry("EncryptedPackage");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void validateDocxArchive(byte[] content) {
        boolean contentTypesFound = false;
        boolean mainDocumentFound = false;
        int entryCount = 0;
        long expandedBytes = 0;
        byte[] buffer = new byte[8192];

        try (ByteArrayInputStream input = new ByteArrayInputStream(content);
             ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_DOCX_ENTRIES || !isSafeZipEntryName(entry.getName())) {
                    throw new IllegalArgumentException("Source document is malformed");
                }

                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    expandedBytes += read;
                    if (entryBytes > MAX_DOCX_ENTRY_BYTES
                            || expandedBytes > MAX_DOCX_EXPANDED_BYTES) {
                        throw new IllegalArgumentException("Source document is malformed");
                    }
                }
                long compressedSize = entry.getCompressedSize();
                if (compressedSize > 0
                        && entryBytes > compressedSize * MAX_DOCX_INFLATE_RATIO) {
                    throw new IllegalArgumentException("Source document is malformed");
                }

                if ("[Content_Types].xml".equals(entry.getName())) {
                    contentTypesFound = true;
                } else if ("word/document.xml".equals(entry.getName())) {
                    mainDocumentFound = true;
                }
                zip.closeEntry();
            }
        } catch (IllegalArgumentException e) {
            if (isStableExtractionFailure(e)) {
                throw e;
            }
            throw new IllegalArgumentException("Source document is malformed", e);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Source document is malformed", e);
        }

        if (entryCount == 0) {
            throw new IllegalArgumentException("Source document is malformed");
        }
        if (!contentTypesFound || !mainDocumentFound) {
            throw new IllegalArgumentException(
                    "Source file content does not match its type"
            );
        }
    }

    private static boolean isSafeZipEntryName(String name) {
        return name != null && !name.isBlank()
                && !name.startsWith("/") && !name.startsWith("\\")
                && name.indexOf('\\') < 0
                && !name.equals("..") && !name.startsWith("../")
                && !name.contains("/../") && !name.endsWith("/..");
    }

    private static String extractDocxBody(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                appendLine(text, paragraph.getText());
            } else if (element instanceof XWPFTable table) {
                appendTable(text, table);
            }
            if (text.length() > MAX_EXTRACTED_CHARACTERS) {
                throw new ExtractedTextLimitException();
            }
        }
        return text.toString();
    }

    private static void appendTable(StringBuilder text, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            boolean firstCell = true;
            for (XWPFTableCell cell : row.getTableCells()) {
                if (!firstCell) {
                    text.append('\t');
                }
                text.append(cell.getText());
                firstCell = false;
            }
            text.append('\n');
        }
    }

    private static void appendLine(StringBuilder text, String value) {
        if (value != null) {
            text.append(value);
        }
        text.append('\n');
    }

    private static boolean hasPdfHeader(byte[] content) {
        int offset = 0;
        while (offset < content.length && offset < 4 && isPdfLeadingWhitespace(content[offset])) {
            offset++;
        }
        if (offset + PDF_MAGIC.length > content.length) {
            return false;
        }
        for (int index = 0; index < PDF_MAGIC.length; index++) {
            if (content[offset + index] != PDF_MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPdfLeadingWhitespace(byte value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStableExtractionFailure(IllegalArgumentException error) {
        String message = error.getMessage();
        if (message == null) {
            return false;
        }
        return switch (message) {
            case "Source document is encrypted",
                 "Source document is malformed",
                 "Source PDF exceeds 300 pages",
                 "Source file content does not match its type",
                 "Source text is empty",
                 "Source text contains NUL characters",
                 "Extracted source text exceeds 500000 characters" -> true;
            default -> false;
        };
    }

    private static final class ExtractedTextLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
