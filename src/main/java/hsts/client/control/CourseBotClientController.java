package hsts.client.control;

import hsts.client.net.Client;
import hsts.common.AddBotQuestionSourcesPayload;
import hsts.common.AddBotTextSourcePayload;
import hsts.common.AskCourseBotPayload;
import hsts.common.BotHistoryDTO;
import hsts.common.BotQuestionResultDTO;
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
import hsts.common.UpdateCourseBotPayload;
import hsts.common.UploadBotSourcePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class CourseBotClientController {
    private static final String REQUEST_FAILED = "Request failed";
    private static final String NULL_RESPONSE = "No response from server";
    private static final String INVALID_BOT = "Invalid Course Bot response from server";
    private static final String INVALID_BOT_LIST =
            "Invalid Course Bot list response from server";
    private static final String INVALID_SOURCE = "Invalid Bot source response from server";
    private static final String INVALID_SOURCE_LIST =
            "Invalid Bot source list response from server";
    private static final String INVALID_USAGE = "Invalid Bot usage response from server";
    private static final String INVALID_HISTORY = "Invalid Bot history response from server";
    private static final String INVALID_ANSWER =
            "Invalid Course Bot answer response from server";

    private final Client client;
    private final Function<Request, Response> requestSender;
    private final Function<Request, Response> courseBotAnswerSender;

    public CourseBotClientController(Client client) {
        this.client = Objects.requireNonNull(client, "client");
        this.requestSender = this.client::sendRequest;
        this.courseBotAnswerSender = this.client::sendCourseBotRequest;
    }

    CourseBotClientController(Function<Request, Response> requestSender) {
        this.client = null;
        this.requestSender = Objects.requireNonNull(requestSender, "requestSender");
        this.courseBotAnswerSender = this.requestSender;
    }

    CourseBotClientController(
            Function<Request, Response> requestSender,
            Function<Request, Response> courseBotAnswerSender
    ) {
        this.client = null;
        this.requestSender = Objects.requireNonNull(requestSender, "requestSender");
        this.courseBotAnswerSender = Objects.requireNonNull(
                courseBotAnswerSender, "courseBotAnswerSender"
        );
    }

    public CompletableFuture<List<CourseBotSummaryDTO>> getMyCourseBots() {
        return send(
                new Request(RequestType.LIST_MY_COURSE_BOTS, null),
                payload -> requireList(payload, CourseBotSummaryDTO.class,
                        INVALID_BOT_LIST)
        );
    }

    public CompletableFuture<CourseBotSummaryDTO> createCourseBot(
            CreateCourseBotPayload payload
    ) {
        return sendMutation(
                payload, "Create Course Bot payload is required",
                RequestType.CREATE_COURSE_BOT,
                response -> requireType(response, CourseBotSummaryDTO.class, INVALID_BOT)
        );
    }

    public CompletableFuture<CourseBotSummaryDTO> updateCourseBot(
            UpdateCourseBotPayload payload
    ) {
        return sendMutation(
                payload, "Update Course Bot payload is required",
                RequestType.UPDATE_COURSE_BOT,
                response -> requireType(response, CourseBotSummaryDTO.class, INVALID_BOT)
        );
    }

    public CompletableFuture<List<BotSourceDTO>> getBotSources(int botId) {
        return sendPositiveBotId(
                botId, RequestType.GET_BOT_SOURCES,
                payload -> requireList(payload, BotSourceDTO.class, INVALID_SOURCE_LIST)
        );
    }

    public CompletableFuture<BotSourceDTO> addTextSource(
            AddBotTextSourcePayload payload
    ) {
        return sendMutation(
                payload, "Bot text source payload is required",
                RequestType.ADD_BOT_TEXT_SOURCE,
                response -> requireType(response, BotSourceDTO.class, INVALID_SOURCE)
        );
    }

    public CompletableFuture<BotSourceDTO> uploadSource(
            UploadBotSourcePayload payload
    ) {
        return sendMutation(
                payload, "Bot file source payload is required",
                RequestType.UPLOAD_BOT_SOURCE,
                response -> requireType(response, BotSourceDTO.class, INVALID_SOURCE)
        );
    }

    public CompletableFuture<List<BotSourceDTO>> addQuestionSources(
            AddBotQuestionSourcesPayload payload
    ) {
        return sendMutation(
                payload, "Bot question sources payload is required",
                RequestType.ADD_BOT_QUESTION_SOURCES,
                response -> requireList(response, BotSourceDTO.class, INVALID_SOURCE_LIST)
        );
    }

    public CompletableFuture<BotSourceDTO> removeSource(
            RemoveBotSourcePayload payload
    ) {
        return sendMutation(
                payload, "Remove Bot source payload is required",
                RequestType.REMOVE_BOT_SOURCE,
                response -> requireType(response, BotSourceDTO.class, INVALID_SOURCE)
        );
    }

    public CompletableFuture<BotUsageSummaryDTO> getBotUsage(int botId) {
        return sendPositiveBotId(
                botId, RequestType.GET_BOT_USAGE,
                payload -> requireType(payload, BotUsageSummaryDTO.class, INVALID_USAGE)
        );
    }

    public CompletableFuture<List<CourseBotSummaryDTO>> getMyAvailableBots() {
        return send(
                new Request(RequestType.LIST_MY_AVAILABLE_BOTS, null),
                payload -> requireList(payload, CourseBotSummaryDTO.class,
                        INVALID_BOT_LIST)
        );
    }

    public CompletableFuture<BotHistoryDTO> getMyBotHistory(int courseId) {
        if (courseId <= 0) {
            return failed(new IllegalArgumentException("Course ID must be positive"));
        }
        return send(
                new Request(RequestType.GET_MY_BOT_HISTORY,
                        new CourseIdPayload(courseId)),
                payload -> requireType(payload, BotHistoryDTO.class, INVALID_HISTORY)
        );
    }

    public CompletableFuture<BotQuestionResultDTO> askCourseBot(
            AskCourseBotPayload payload
    ) {
        return sendMutation(
                payload, "Ask Course Bot payload is required",
                RequestType.ASK_COURSE_BOT,
                response -> requireType(
                        response, BotQuestionResultDTO.class, INVALID_ANSWER
                ), courseBotAnswerSender
        );
    }

    private <T, P> CompletableFuture<T> sendMutation(
            P payload, String nullMessage, RequestType type,
            Function<Object, T> responseMapper
    ) {
        if (payload == null) {
            return failed(new IllegalArgumentException(nullMessage));
        }
        return send(new Request(type, payload), responseMapper);
    }

    private <T, P> CompletableFuture<T> sendMutation(
            P payload, String nullMessage, RequestType type,
            Function<Object, T> responseMapper,
            Function<Request, Response> sender
    ) {
        if (payload == null) {
            return failed(new IllegalArgumentException(nullMessage));
        }
        return send(new Request(type, payload), responseMapper, sender);
    }

    private <T> CompletableFuture<T> sendPositiveBotId(
            int botId, RequestType type, Function<Object, T> responseMapper
    ) {
        if (botId <= 0) {
            return failed(new IllegalArgumentException("Bot ID must be positive"));
        }
        return send(new Request(type, new CourseBotIdPayload(botId)), responseMapper);
    }

    private <T> CompletableFuture<T> send(
            Request request, Function<Object, T> responseMapper
    ) {
        return send(request, responseMapper, requestSender);
    }

    private <T> CompletableFuture<T> send(
            Request request, Function<Object, T> responseMapper,
            Function<Request, Response> sender
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Response response = sender.apply(request);
            if (response == null) {
                throw new IllegalStateException(NULL_RESPONSE);
            }
            if (!response.isSuccess()) {
                String message = response.getMessage();
                throw new IllegalStateException(
                        message == null || message.isBlank() ? REQUEST_FAILED : message
                );
            }
            return responseMapper.apply(response.getPayload());
        });
    }

    private static <T> T requireType(
            Object payload, Class<T> type, String invalidMessage
    ) {
        if (!type.isInstance(payload)) {
            throw new IllegalStateException(invalidMessage);
        }
        return type.cast(payload);
    }

    private static <T> List<T> requireList(
            Object payload, Class<T> elementType, String invalidMessage
    ) {
        if (!(payload instanceof List<?> rawList)) {
            throw new IllegalStateException(invalidMessage);
        }
        List<T> validated = new ArrayList<>(rawList.size());
        for (Object element : rawList) {
            if (!elementType.isInstance(element)) {
                throw new IllegalStateException(invalidMessage);
            }
            validated.add(elementType.cast(element));
        }
        return List.copyOf(validated);
    }

    private static <T> CompletableFuture<T> failed(RuntimeException exception) {
        return CompletableFuture.failedFuture(exception);
    }
}
