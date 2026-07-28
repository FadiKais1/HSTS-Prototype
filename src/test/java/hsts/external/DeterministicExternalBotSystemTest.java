package hsts.external;

import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotSourceType;
import hsts.server.bot.DeterministicExternalBotSystem;
import org.junit.Test;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DeterministicExternalBotSystemTest {
    private final ExternalBotSystem system = new DeterministicExternalBotSystem();

    @Test
    public void groundedMatchingIsCaseInsensitiveAndDerivedFromSource() {
        BotProviderResponse response = system.answer(request(
                List.of(source("A limit describes function behavior near a point.")),
                "WHAT is a LIMIT?"
        ));

        assertEquals(BotAnswerStatus.ANSWERED, response.getAnswerStatus());
        assertEquals("A limit describes function behavior near a point.",
                response.getAnswerText());
    }

    @Test
    public void tiesUseSourceThenPassageOrderAndUnrelatedQuestionsReturnNoAnswer() {
        BotProviderResponse tied = system.answer(request(List.of(
                source("Vectors have magnitude.\n\nVectors have direction."),
                source("Vectors belong to a vector space.")), "Explain vectors"
        ));
        assertEquals("Vectors have magnitude.", tied.getAnswerText());

        BotProviderResponse unrelated = system.answer(request(
                List.of(source("Limits describe nearby behavior.")), "volcanic geology"
        ));
        assertEquals(BotAnswerStatus.NO_SUITABLE_ANSWER,
                unrelated.getAnswerStatus());
        assertEquals("", unrelated.getAnswerText());
    }

    @Test
    public void answerIsBoundedAndProviderValuesAreImmutableNonSerializable() {
        String passage = "calculus " + "x".repeat(5_000);
        BotProviderResponse response = system.answer(request(
                List.of(source(passage)), "calculus"
        ));
        assertEquals(4_000, response.getAnswerText().length());

        BotProviderRequest request = request(List.of(source("calculus")), "calculus");
        assertThrows(UnsupportedOperationException.class,
                () -> request.getSources().add(source("other")));
        assertFalse(Serializable.class.isAssignableFrom(BotProviderRequest.class));
        assertFalse(Serializable.class.isAssignableFrom(BotProviderResponse.class));
        assertTrue(ExternalBotSystem.class.isInterface());
    }

    @Test
    public void providerBoundaryContainsNoHstsIdentityOrInfrastructureFields() {
        String fields = java.util.Arrays.stream(BotProviderRequest.class.getDeclaredFields())
                .map(field -> field.getName() + ":" + field.getType().getName())
                .reduce("", (left, right) -> left + " " + right).toLowerCase();
        assertFalse(fields.contains("student"));
        assertFalse(fields.contains("userid"));
        assertFalse(fields.contains("apikey"));
        assertFalse(fields.contains("repository"));
        assertFalse(fields.contains("request.class"));
    }

    private static BotProviderRequest request(List<BotProviderSource> sources,
                                               String question) {
        return new BotProviderRequest(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString(),
                "Course Bot", "Calculus", sources, List.of(), question
        );
    }

    private static BotProviderSource source(String text) {
        return new BotProviderSource("Notes", BotSourceType.FREE_TEXT, text);
    }
}
