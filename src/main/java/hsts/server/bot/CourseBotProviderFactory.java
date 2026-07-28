package hsts.server.bot;

import hsts.external.ExternalBotSystem;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public final class CourseBotProviderFactory {
    static final String PROVIDER_VARIABLE = "HSTS_BOT_PROVIDER";
    static final String API_KEY_VARIABLE = "HSTS_GEMINI_API_KEY";
    static final String MODEL_VARIABLE = "HSTS_GEMINI_MODEL";
    static final String TIMEOUT_VARIABLE = "HSTS_GEMINI_TIMEOUT_SECONDS";
    static final String BASE_URL_VARIABLE = "HSTS_GEMINI_BASE_URL";

    static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    static final int DEFAULT_TIMEOUT_SECONDS = 30;
    static final int MAX_TIMEOUT_SECONDS = 300;
    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private CourseBotProviderFactory() {
    }

    public static ExternalBotSystem createFromEnvironment() {
        return create(System.getenv());
    }

    static ExternalBotSystem create(Map<String, String> environment) {
        String provider = normalized(environment.get(PROVIDER_VARIABLE));
        if (provider == null || provider.equals("DETERMINISTIC")) {
            return new DeterministicExternalBotSystem();
        }
        if (!provider.equals("GEMINI")) {
            throw new IllegalStateException("Course Bot provider is invalid");
        }

        String apiKey = required(environment.get(API_KEY_VARIABLE),
                "Gemini API key is required");
        String model = valueOrDefault(environment.get(MODEL_VARIABLE), DEFAULT_MODEL,
                "Gemini model is required");
        Duration timeout = parseTimeout(environment.get(TIMEOUT_VARIABLE));
        URI baseUri = parseProductionBaseUri(environment.get(BASE_URL_VARIABLE));
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        return new GeminiExternalBotSystem(client, baseUri, apiKey, model, timeout);
    }

    private static Duration parseTimeout(String value) {
        String normalized = normalized(value);
        if (normalized == null) {
            return Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
        }
        try {
            int seconds = Integer.parseInt(normalized);
            if (seconds < 1 || seconds > MAX_TIMEOUT_SECONDS) {
                throw new NumberFormatException();
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Gemini timeout is invalid", exception);
        }
    }

    private static URI parseProductionBaseUri(String value) {
        String normalized = valueOrDefault(value, DEFAULT_BASE_URL,
                "Gemini endpoint is invalid");
        try {
            URI uri = URI.create(normalized);
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Gemini endpoint is invalid", exception);
        }
    }

    private static String required(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return normalized;
    }

    private static String valueOrDefault(String value, String defaultValue,
                                         String blankMessage) {
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException(blankMessage);
        }
        return normalized;
    }

    private static String normalized(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
