package hsts.client.control;

import hsts.common.AddBotQuestionSourcesPayload;
import hsts.common.AddBotTextSourcePayload;
import hsts.common.AskCourseBotPayload;
import hsts.common.BotHistoryDTO;
import hsts.common.BotMessageDTO;
import hsts.common.BotQuestionResultDTO;
import hsts.common.BotQuestionVersionReference;
import hsts.common.BotSourceDTO;
import hsts.common.BotUsageSummaryDTO;
import hsts.common.CourseBotIdPayload;
import hsts.common.CourseBotSummaryDTO;
import hsts.common.CourseIdPayload;
import hsts.common.CreateCourseBotPayload;
import hsts.common.RemoveBotSourcePayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.UpdateCourseBotPayload;
import hsts.common.UploadBotSourcePayload;
import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;
import hsts.common.type.BotStatus;
import org.junit.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CourseBotClientControllerTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 7, 20, 12, 0);

    @Test
    public void allApisSendExactRequestTypesAndPayloads() {
        RecordingSender sender = new RecordingSender();
        CourseBotClientController controller = new CourseBotClientController(sender::send);
        CourseBotSummaryDTO bot = bot(7);
        BotSourceDTO source = source(9);
        BotUsageSummaryDTO usage = new BotUsageSummaryDTO(
                7, 1, "Bot", "Course", 0, null, List.of()
        );
        BotHistoryDTO history = new BotHistoryDTO(7, 1, "Bot", "Course", List.of());
        BotQuestionResultDTO answer = new BotQuestionResultDTO(
                new BotMessageDTO(1, 1, "Question", "Answer",
                        BotAnswerStatus.ANSWERED, TIME), true
        );
        CreateCourseBotPayload create = new CreateCourseBotPayload(1, "Bot");
        UpdateCourseBotPayload update = new UpdateCourseBotPayload(
                7, "Bot", BotStatus.ACTIVE
        );
        AddBotTextSourcePayload text = new AddBotTextSourcePayload(7, "Notes", "Text");
        UploadBotSourcePayload upload = new UploadBotSourcePayload(
                7, "notes.txt", BotSourceType.TXT, new byte[]{1, 2, 3}
        );
        AddBotQuestionSourcesPayload questions = new AddBotQuestionSourcesPayload(
                7, List.of(new BotQuestionVersionReference(11, 2))
        );
        RemoveBotSourcePayload remove = new RemoveBotSourcePayload(7, 9);
        AskCourseBotPayload ask = new AskCourseBotPayload(1, "limits");

        sender.response = Response.success("ok", List.of(bot));
        assertEquals(List.of(bot), controller.getMyCourseBots().join());
        assertRequest(sender.last(), RequestType.LIST_MY_COURSE_BOTS, null);
        sender.response = Response.success("ok", bot);
        assertSame(bot, controller.createCourseBot(create).join());
        assertRequest(sender.last(), RequestType.CREATE_COURSE_BOT, create);
        assertSame(bot, controller.updateCourseBot(update).join());
        assertRequest(sender.last(), RequestType.UPDATE_COURSE_BOT, update);
        sender.response = Response.success("ok", List.of(source));
        assertEquals(List.of(source), controller.getBotSources(7).join());
        assertId(sender.last(), RequestType.GET_BOT_SOURCES, 7);
        sender.response = Response.success("ok", source);
        assertSame(source, controller.addTextSource(text).join());
        assertRequest(sender.last(), RequestType.ADD_BOT_TEXT_SOURCE, text);
        assertSame(source, controller.uploadSource(upload).join());
        assertRequest(sender.last(), RequestType.UPLOAD_BOT_SOURCE, upload);
        sender.response = Response.success("ok", List.of(source));
        assertEquals(List.of(source), controller.addQuestionSources(questions).join());
        assertRequest(sender.last(), RequestType.ADD_BOT_QUESTION_SOURCES, questions);
        sender.response = Response.success("ok", source);
        assertSame(source, controller.removeSource(remove).join());
        assertRequest(sender.last(), RequestType.REMOVE_BOT_SOURCE, remove);
        sender.response = Response.success("ok", usage);
        assertSame(usage, controller.getBotUsage(7).join());
        assertId(sender.last(), RequestType.GET_BOT_USAGE, 7);
        sender.response = Response.success("ok", List.of(bot));
        assertEquals(List.of(bot), controller.getMyAvailableBots().join());
        assertRequest(sender.last(), RequestType.LIST_MY_AVAILABLE_BOTS, null);
        sender.response = Response.success("ok", history);
        assertSame(history, controller.getMyBotHistory(1).join());
        assertEquals(RequestType.GET_MY_BOT_HISTORY, sender.last().getType());
        assertEquals(1, ((CourseIdPayload) sender.last().getPayload()).getCourseId());
        sender.response = Response.success("ok", answer);
        assertSame(answer, controller.askCourseBot(ask).join());
        assertRequest(sender.last(), RequestType.ASK_COURSE_BOT, ask);
    }

    @Test
    public void onlyAskCourseBotUsesTheDedicatedLongRunningSender() {
        RecordingSender ordinary = new RecordingSender();
        RecordingSender courseBot = new RecordingSender();
        CourseBotClientController controller = new CourseBotClientController(
                ordinary::send, courseBot::send
        );
        CourseBotSummaryDTO bot = bot(7);
        ordinary.response = Response.success("ok", List.of(bot));
        courseBot.response = Response.success("ok", new BotQuestionResultDTO(
                new BotMessageDTO(1, 1, "Question", "Answer",
                        BotAnswerStatus.ANSWERED, TIME), true
        ));

        controller.getMyCourseBots().join();
        controller.askCourseBot(new AskCourseBotPayload(1, "limits")).join();

        assertEquals(1, ordinary.requests.size());
        assertEquals(RequestType.LIST_MY_COURSE_BOTS, ordinary.last().getType());
        assertEquals(1, courseBot.requests.size());
        assertEquals(RequestType.ASK_COURSE_BOT, courseBot.last().getType());
    }

    @Test
    public void idsAndNullMutationsFailBeforeNetworking() {
        RecordingSender sender = new RecordingSender();
        CourseBotClientController controller = new CourseBotClientController(sender::send);
        assertFutureError(() -> controller.getBotSources(0),
                IllegalArgumentException.class, "Bot ID must be positive");
        assertFutureError(() -> controller.getBotUsage(-1),
                IllegalArgumentException.class, "Bot ID must be positive");
        assertFutureError(() -> controller.getMyBotHistory(0),
                IllegalArgumentException.class, "Course ID must be positive");
        assertFutureError(() -> controller.createCourseBot(null),
                IllegalArgumentException.class, "Create Course Bot payload is required");
        assertFutureError(() -> controller.updateCourseBot(null),
                IllegalArgumentException.class, "Update Course Bot payload is required");
        assertFutureError(() -> controller.addTextSource(null),
                IllegalArgumentException.class, "Bot text source payload is required");
        assertFutureError(() -> controller.uploadSource(null),
                IllegalArgumentException.class, "Bot file source payload is required");
        assertFutureError(() -> controller.addQuestionSources(null),
                IllegalArgumentException.class, "Bot question sources payload is required");
        assertFutureError(() -> controller.removeSource(null),
                IllegalArgumentException.class, "Remove Bot source payload is required");
        assertFutureError(() -> controller.askCourseBot(null),
                IllegalArgumentException.class, "Ask Course Bot payload is required");
        assertTrue(sender.requests.isEmpty());
    }

    @Test
    public void listResponsesAreStrictOrderedDefensiveAndAllowEmpty() {
        RecordingSender sender = new RecordingSender();
        CourseBotClientController controller = new CourseBotClientController(sender::send);
        List<CourseBotSummaryDTO> mutable = new ArrayList<>(List.of(bot(8), bot(7)));
        sender.response = Response.success("ok", mutable);
        List<CourseBotSummaryDTO> result = controller.getMyCourseBots().join();
        mutable.clear();
        assertEquals(List.of(8, 7), result.stream()
                .map(CourseBotSummaryDTO::getBotId).toList());
        assertThrows(UnsupportedOperationException.class, result::clear);

        sender.response = Response.success("ok", List.of());
        assertTrue(controller.getMyAvailableBots().join().isEmpty());
        sender.response = Response.success("ok", java.util.Arrays.asList(source(9), null));
        assertFutureError(() -> controller.getBotSources(7),
                IllegalStateException.class, "Invalid Bot source list response from server");
        sender.response = Response.success("ok", List.of(bot(7), "wrong"));
        assertFutureError(controller::getMyCourseBots,
                IllegalStateException.class, "Invalid Course Bot list response from server");
    }

    @Test
    public void nullErrorsBlankErrorsAndWrongPayloadsUseStableMessages() {
        RecordingSender sender = new RecordingSender();
        CourseBotClientController controller = new CourseBotClientController(sender::send);
        sender.response = null;
        assertFutureError(controller::getMyCourseBots,
                IllegalStateException.class, "No response from server");
        sender.response = Response.error("Bot denied");
        assertFutureError(controller::getMyAvailableBots,
                IllegalStateException.class, "Bot denied");
        sender.response = new Response(ResponseStatus.ERROR, " ", null);
        assertFutureError(() -> controller.getBotUsage(7),
                IllegalStateException.class, "Request failed");

        sender.response = Response.success("ok", null);
        assertFutureError(() -> controller.createCourseBot(
                        new CreateCourseBotPayload(1, "Bot")),
                IllegalStateException.class, "Invalid Course Bot response from server");
        assertFutureError(() -> controller.addTextSource(
                        new AddBotTextSourcePayload(7, "Notes", "Text")),
                IllegalStateException.class, "Invalid Bot source response from server");
        assertFutureError(() -> controller.getBotUsage(7),
                IllegalStateException.class, "Invalid Bot usage response from server");
        assertFutureError(() -> controller.getMyBotHistory(1),
                IllegalStateException.class, "Invalid Bot history response from server");
        assertFutureError(() -> controller.askCourseBot(
                        new AskCourseBotPayload(1, "limits")),
                IllegalStateException.class,
                "Invalid Course Bot answer response from server");
    }

    @Test
    public void controllerHasNoActorFileLifecycleOrJavaFxApi() {
        List<String> methods = java.util.Arrays.stream(
                        CourseBotClientController.class.getDeclaredMethods())
                .map(method -> method.getName() + java.util.Arrays.toString(
                        method.getParameterTypes()))
                .toList();
        assertTrue(methods.stream().noneMatch(value ->
                value.contains("studentId") || value.contains("teacherId")
                        || value.contains(Path.class.getName())
                        || value.contains("javafx") || value.contains("close")
                        || value.contains("connect") || value.contains("disconnect")));
    }

    private static void assertRequest(Request request, RequestType type, Object payload) {
        assertEquals(type, request.getType());
        if (payload == null) {
            assertNull(request.getPayload());
        } else {
            assertSame(payload, request.getPayload());
        }
    }

    private static void assertId(Request request, RequestType type, int botId) {
        assertEquals(type, request.getType());
        assertEquals(botId, ((CourseBotIdPayload) request.getPayload()).getBotId());
    }

    private static void assertFutureError(
            Supplier<CompletableFuture<?>> operation,
            Class<? extends RuntimeException> type,
            String message
    ) {
        CompletionException exception = assertThrows(
                CompletionException.class, () -> operation.get().join()
        );
        assertEquals(type, exception.getCause().getClass());
        assertEquals(message, exception.getCause().getMessage());
    }

    private static CourseBotSummaryDTO bot(int id) {
        return new CourseBotSummaryDTO(
                id, 1, "Bot " + id, "Course", BotStatus.ACTIVE, 1, TIME, TIME
        );
    }

    private static BotSourceDTO source(int id) {
        return new BotSourceDTO(
                id, 7, BotSourceType.FREE_TEXT, "Notes", null, null,
                BotSourceStatus.ACTIVE, TIME, null
        );
    }

    private static final class RecordingSender {
        final List<Request> requests = new ArrayList<>();
        Response response;

        Response send(Request request) {
            requests.add(request);
            return response;
        }

        Request last() {
            return requests.get(requests.size() - 1);
        }
    }
}
