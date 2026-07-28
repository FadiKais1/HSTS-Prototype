package hsts.server.bot;

import hsts.external.ExternalBotSystem;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CourseBotProviderFactoryTest {
    @Test
    public void absentBlankAndExplicitDeterministicSelectOfflineProvider() {
        assertTrue(CourseBotProviderFactory.create(Map.of())
                instanceof DeterministicExternalBotSystem);
        assertTrue(CourseBotProviderFactory.create(Map.of("HSTS_BOT_PROVIDER", "  "))
                instanceof DeterministicExternalBotSystem);
        assertTrue(CourseBotProviderFactory.create(Map.of(
                "HSTS_BOT_PROVIDER", " deterministic "))
                instanceof DeterministicExternalBotSystem);
    }

    @Test
    public void rejectsInvalidProviderAndExplicitGeminiWithoutKey() {
        assertMessage("Course Bot provider is invalid",
                Map.of("HSTS_BOT_PROVIDER", "other"));
        assertMessage("Gemini API key is required",
                Map.of("HSTS_BOT_PROVIDER", "GEMINI"));
    }

    @Test
    public void geminiDefaultsAreExactAndDoNotFallBack() throws Exception {
        ExternalBotSystem provider = CourseBotProviderFactory.create(Map.of(
                "HSTS_BOT_PROVIDER", " gemini ",
                "HSTS_GEMINI_API_KEY", "fake-key"
        ));
        assertTrue(provider instanceof GeminiExternalBotSystem);
        assertEquals(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-2.5-flash-lite:generateContent"),
                field(provider, "endpoint"));
        assertEquals(Duration.ofSeconds(30), field(provider, "timeout"));
    }

    @Test
    public void validatesModelTimeoutAndProductionHttpsEndpoint() {
        Map<String, String> base = gemini();
        assertMessage("Gemini model is required", with(base, "HSTS_GEMINI_MODEL", " "));
        for (String timeout : new String[]{"0", "-1", "301", "abc"}) {
            assertMessage("Gemini timeout is invalid",
                    with(base, "HSTS_GEMINI_TIMEOUT_SECONDS", timeout));
        }
        for (String endpoint : new String[]{"not-a-uri", "http://localhost:8080",
                "https://user@example.com", "https://example.com?q=x"}) {
            assertMessage("Gemini endpoint is invalid",
                    with(base, "HSTS_GEMINI_BASE_URL", endpoint));
        }
    }

    @Test
    public void explicitValuesAreTrimmedAndBounded() throws Exception {
        Map<String, String> values = gemini();
        values.put("HSTS_GEMINI_MODEL", " gemini-test ");
        values.put("HSTS_GEMINI_TIMEOUT_SECONDS", " 300 ");
        values.put("HSTS_GEMINI_BASE_URL", " https://example.test/ ");
        ExternalBotSystem provider = CourseBotProviderFactory.create(values);
        assertEquals(URI.create("https://example.test/v1beta/models/gemini-test:generateContent"),
                field(provider, "endpoint"));
        assertEquals(Duration.ofSeconds(300), field(provider, "timeout"));
    }

    private static Map<String, String> gemini() {
        Map<String, String> values = new HashMap<>();
        values.put("HSTS_BOT_PROVIDER", "GEMINI");
        values.put("HSTS_GEMINI_API_KEY", "fake-key");
        return values;
    }

    private static Map<String, String> with(Map<String, String> source,
                                            String key, String value) {
        Map<String, String> copy = new HashMap<>(source);
        copy.put(key, value);
        return copy;
    }

    private static void assertMessage(String expected, Map<String, String> values) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> CourseBotProviderFactory.create(values));
        assertEquals(expected, failure.getMessage());
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = GeminiExternalBotSystem.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
