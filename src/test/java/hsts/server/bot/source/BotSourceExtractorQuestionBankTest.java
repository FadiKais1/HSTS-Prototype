package hsts.server.bot.source;

import hsts.common.type.BotSourceType;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BotSourceExtractorQuestionBankTest {
    private final BotSourceExtractor extractor = new BotSourceExtractor();

    @Test
    public void trustedQuestionBankExtractionNormalizesAndUsesSharedChecksum() {
        ExtractedBotSource result = extractor.extractQuestionBankSource(
                "  Question 17 version 2  ",
                "\uFEFF  Topic: Calculus  \r\nQuestion: What is a limit?  \rAnswer: L  "
        );

        assertEquals(BotSourceType.QUESTION_BANK, result.getSourceType());
        assertEquals("Question 17 version 2", result.getDisplayName());
        assertEquals(
                "Topic: Calculus\nQuestion: What is a limit?\nAnswer: L",
                result.getExtractedText()
        );
        assertEquals(
                extractor.extractFreeText("Comparison", result.getExtractedText())
                        .getContentSha256(),
                result.getContentSha256()
        );
    }

    @Test
    public void trustedQuestionBankExtractionRejectsBlankAndOverLimitText() {
        assertMessage("Source text is empty",
                () -> extractor.extractQuestionBankSource("Question", " \r\n\t "));
        String atLimit = "q".repeat(BotSourceExtractor.MAX_EXTRACTED_CHARACTERS);
        assertEquals(atLimit, extractor.extractQuestionBankSource("Question", atLimit)
                .getExtractedText());
        assertMessage("Extracted source text exceeds 500000 characters",
                () -> extractor.extractQuestionBankSource("Question", atLimit + "q"));
    }

    @Test
    public void trustedQuestionBankExtractionUsesExistingSafeNameValidation() {
        assertMessage("Source file name is required",
                () -> extractor.extractQuestionBankSource(" ", "text"));
        for (String name : new String[]{"../question", "folder/question",
                "folder\\question", ".", "..", "bad\0name"}) {
            assertMessage("Source file name is invalid",
                    () -> extractor.extractQuestionBankSource(name, "text"));
        }
    }

    @Test
    public void fileExtractionStillRejectsNonFileSourceTypesAndKeepsExtensionRules() {
        byte[] content = "trusted text".getBytes(StandardCharsets.UTF_8);
        assertMessage("Source file type is invalid",
                () -> extractor.extractFile(
                        "question.txt", BotSourceType.QUESTION_BANK, content
                ));
        assertMessage("Source file type is invalid",
                () -> extractor.extractFile(
                        "notes.txt", BotSourceType.FREE_TEXT, content
                ));
        assertMessage("Source file extension does not match its type",
                () -> extractor.extractFile("notes.pdf", BotSourceType.TXT, content));
        assertEquals(BotSourceType.TXT,
                extractor.extractFile("notes.txt", BotSourceType.TXT, content)
                        .getSourceType());
    }

    @Test
    public void publicApisRemainExplicitAndContainNoGenericTypeBypass() {
        Set<String> publicMethods = Arrays.stream(BotSourceExtractor.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "extractFreeText", "extractQuestionBankSource", "extractFile"
        ), publicMethods);
        assertEquals(
                extractor.extractFreeText("Notes", "alpha\r\nbeta").getContentSha256(),
                extractor.extractFreeText("Other", "alpha\nbeta").getContentSha256()
        );
        assertTrue(Arrays.stream(BotSourceExtractor.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().startsWith("java.sql")));
    }

    private static void assertMessage(String expected, Runnable operation) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, operation::run
        );
        assertEquals(expected, error.getMessage());
    }
}
