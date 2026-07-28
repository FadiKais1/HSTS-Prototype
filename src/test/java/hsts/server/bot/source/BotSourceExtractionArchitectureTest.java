package hsts.server.bot.source;

import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotSourceExtractionArchitectureTest {
    private static final Path SOURCE_ROOT = Path.of(
            "src", "main", "java", "hsts", "server", "bot", "source"
    );

    @Test
    public void extractionValueIsFinalImmutableAndNonSerializable() {
        assertTrue(Modifier.isFinal(ExtractedBotSource.class.getModifiers()));
        assertFalse(Serializable.class.isAssignableFrom(ExtractedBotSource.class));
        assertFalse(Serializable.class.isAssignableFrom(BotSourceExtractor.class));
        for (Field field : ExtractedBotSource.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertFalse(field.getType().isArray());
        }
        for (Method method : ExtractedBotSource.class.getDeclaredMethods()) {
            assertFalse(method.getName().startsWith("set"));
        }
    }

    @Test
    public void extractionPackageHasNoForbiddenArchitectureOrSecurityDependency()
            throws Exception {
        String source = readAllJavaSources().toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "java.sql", "hsts.server.repository",
                "javafx", "hsts.ocsf", "hsts.client", "localdatetime.now",
                "files.createtemp", "file.createtemp", "api_key", "provider_secret",
                "passwordhash", "filesystem path"
        )) {
            assertFalse("Forbidden extraction dependency: " + forbidden,
                    source.contains(forbidden));
        }
        assertFalse(source.contains("implements serializable"));
    }

    @Test
    public void resultContainsNoRawBytesPathOrSecretField() {
        for (Field field : ExtractedBotSource.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.contains("byte"));
            assertFalse(name.contains("path"));
            assertFalse(name.contains("key"));
            assertFalse(name.contains("secret"));
        }
    }

    private static String readAllJavaSources() throws IOException {
        StringBuilder text = new StringBuilder();
        try (var paths = Files.list(SOURCE_ROOT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .toList()) {
                text.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return text.toString();
    }
}
