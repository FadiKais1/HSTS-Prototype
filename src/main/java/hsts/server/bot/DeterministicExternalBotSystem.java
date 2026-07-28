package hsts.server.bot;

import hsts.common.type.BotAnswerStatus;
import hsts.external.BotProviderRequest;
import hsts.external.BotProviderResponse;
import hsts.external.BotProviderSource;
import hsts.external.ExternalBotSystem;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class DeterministicExternalBotSystem implements ExternalBotSystem {
    private static final int MAX_ANSWER_LENGTH = 4_000;
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("(?:\\R\\s*){2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "how", "in", "is", "it", "of", "on", "or", "that", "the", "this",
            "to", "what", "when", "where", "which", "who", "why", "with"
    );

    @Override
    public BotProviderResponse answer(BotProviderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Bot provider request is required");
        }
        Set<String> questionTerms = tokens(request.getQuestionText());
        if (questionTerms.isEmpty()) {
            return noAnswer();
        }

        String bestPassage = null;
        int bestScore = 0;
        for (BotProviderSource source : request.getSources()) {
            String[] passages = PARAGRAPH_BREAK.split(source.getExtractedText());
            for (String passage : passages) {
                int score = overlap(questionTerms, tokens(passage));
                if (score > bestScore) {
                    bestScore = score;
                    bestPassage = passage.trim();
                }
            }
        }
        if (bestScore == 0 || bestPassage == null || bestPassage.isEmpty()) {
            return noAnswer();
        }
        String answer = bestPassage.length() <= MAX_ANSWER_LENGTH
                ? bestPassage : bestPassage.substring(0, MAX_ANSWER_LENGTH);
        return new BotProviderResponse(BotAnswerStatus.ANSWERED, answer, null);
    }

    private static BotProviderResponse noAnswer() {
        return new BotProviderResponse(BotAnswerStatus.NO_SUITABLE_ANSWER, "", null);
    }

    private static int overlap(Set<String> left, Set<String> right) {
        int score = 0;
        for (String term : left) {
            if (right.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private static Set<String> tokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        Arrays.stream(NON_ALPHANUMERIC.split(value.toLowerCase(Locale.ROOT)))
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .forEach(tokens::add);
        return tokens;
    }
}
