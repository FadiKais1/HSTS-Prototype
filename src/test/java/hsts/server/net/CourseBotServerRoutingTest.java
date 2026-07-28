package hsts.server.net;

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
import hsts.common.UpdateCourseBotPayload;
import hsts.common.UploadBotSourcePayload;
import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;
import hsts.common.type.BotStatus;
import hsts.server.control.CourseBotService;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CourseBotServerRoutingTest {
    private static final int USER_ID = 1002;
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 7, 20, 12, 0);

    @Test
    public void allAuthenticatedRoutesUseMetadataIdentityExactPayloadAndMessages() {
        RecordingService service = new RecordingService();
        Server server = server(service);
        CreateCourseBotPayload create = new CreateCourseBotPayload(1, "Bot");
        UpdateCourseBotPayload update = new UpdateCourseBotPayload(
                7, "Bot", BotStatus.ACTIVE
        );
        AddBotTextSourcePayload text = new AddBotTextSourcePayload(7, "Notes", "Text");
        UploadBotSourcePayload upload = new UploadBotSourcePayload(
                7, "notes.txt", BotSourceType.TXT, new byte[]{1}
        );
        AddBotQuestionSourcesPayload questions = new AddBotQuestionSourcesPayload(
                7, List.of(new BotQuestionVersionReference(11, 2))
        );
        RemoveBotSourcePayload remove = new RemoveBotSourcePayload(7, 9);
        AskCourseBotPayload ask = new AskCourseBotPayload(1, "limits");

        assertRoute(server, service, RequestType.LIST_MY_COURSE_BOTS, null,
                "Course Bots loaded");
        assertRoute(server, service, RequestType.CREATE_COURSE_BOT, create,
                "Course Bot created");
        assertSame(create, service.lastPayload);
        assertRoute(server, service, RequestType.UPDATE_COURSE_BOT, update,
                "Course Bot updated");
        assertSame(update, service.lastPayload);
        assertRoute(server, service, RequestType.GET_BOT_SOURCES,
                new CourseBotIdPayload(7), "Bot sources loaded");
        assertRoute(server, service, RequestType.ADD_BOT_TEXT_SOURCE, text,
                "Bot text source added");
        assertSame(text, service.lastPayload);
        assertRoute(server, service, RequestType.UPLOAD_BOT_SOURCE, upload,
                "Bot file source added");
        assertSame(upload, service.lastPayload);
        assertRoute(server, service, RequestType.ADD_BOT_QUESTION_SOURCES, questions,
                "Bot question sources added");
        assertSame(questions, service.lastPayload);
        assertRoute(server, service, RequestType.REMOVE_BOT_SOURCE, remove,
                "Bot source removed");
        assertSame(remove, service.lastPayload);
        assertRoute(server, service, RequestType.GET_BOT_USAGE,
                new CourseBotIdPayload(7), "Bot usage loaded");
        assertRoute(server, service, RequestType.LIST_MY_AVAILABLE_BOTS, null,
                "Available Course Bots loaded");
        assertRoute(server, service, RequestType.GET_MY_BOT_HISTORY,
                new CourseIdPayload(1), "Bot history loaded");
        assertRoute(server, service, RequestType.ASK_COURSE_BOT, ask,
                "Course Bot answer received");
        assertSame(ask, service.lastPayload);

        assertEquals(12, service.calls.size());
        assertTrue(service.userIds.stream().allMatch(value -> value == USER_ID));
    }

    @Test
    public void wrongPayloadsNeverReachServiceAndUseExactErrors() {
        RecordingService service = new RecordingService();
        Server server = server(service);
        assertInvalid(server, RequestType.LIST_MY_COURSE_BOTS, "wrong",
                "Course Bot list payload must be empty");
        assertInvalid(server, RequestType.CREATE_COURSE_BOT, null,
                "Create Course Bot payload is required");
        assertInvalid(server, RequestType.UPDATE_COURSE_BOT, "wrong",
                "Update Course Bot payload is required");
        assertInvalid(server, RequestType.GET_BOT_SOURCES, 7,
                "Course Bot ID payload is required");
        assertInvalid(server, RequestType.ADD_BOT_TEXT_SOURCE, null,
                "Bot text source payload is required");
        assertInvalid(server, RequestType.UPLOAD_BOT_SOURCE, null,
                "Bot file source payload is required");
        assertInvalid(server, RequestType.ADD_BOT_QUESTION_SOURCES, null,
                "Bot question sources payload is required");
        assertInvalid(server, RequestType.REMOVE_BOT_SOURCE, null,
                "Remove Bot source payload is required");
        assertInvalid(server, RequestType.GET_BOT_USAGE, "wrong",
                "Course Bot ID payload is required");
        assertInvalid(server, RequestType.LIST_MY_AVAILABLE_BOTS, 1,
                "Available Bot list payload must be empty");
        assertInvalid(server, RequestType.GET_MY_BOT_HISTORY, 1,
                "Course ID payload is required");
        assertInvalid(server, RequestType.ASK_COURSE_BOT, null,
                "Ask Course Bot payload is required");
        assertTrue(service.calls.isEmpty());
    }

    @Test
    public void allBotRoutesRejectContextFreeCallsWithoutInvokingService() {
        RecordingService service = new RecordingService();
        Server server = server(service);
        for (RequestType type : botTypes()) {
            Response response = server.handleRequest(new Request(type, null));
            assertFalse(response.isSuccess());
            assertEquals("Authentication context required", response.getMessage());
        }
        assertTrue(service.calls.isEmpty());
    }

    @Test
    public void serviceErrorsAreSafeAndCompatibilityConstructorFailsClearly() {
        RecordingService service = new RecordingService();
        service.failureMessage = "Course Bot denied";
        Response denied = server(service).handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_COURSE_BOTS, null), USER_ID
        );
        assertEquals("Course Bot denied", denied.getMessage());

        service.failureMessage = " ";
        Response blank = server(service).handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_COURSE_BOTS, null), USER_ID
        );
        assertEquals("Request failed", blank.getMessage());

        Server compatibility = new Server(0, null, null);
        Response unavailable = compatibility.handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_COURSE_BOTS, null), USER_ID
        );
        assertEquals("Course Bot service is unavailable", unavailable.getMessage());
    }

    private static void assertRoute(Server server, RecordingService service,
                                    RequestType type, Object payload, String message) {
        Response response = server.handleAuthenticatedRequest(
                new Request(type, payload), USER_ID
        );
        assertTrue(response.isSuccess());
        assertEquals(message, response.getMessage());
        assertEquals(type, service.calls.get(service.calls.size() - 1));
    }

    private static void assertInvalid(Server server, RequestType type,
                                      Object payload, String message) {
        Response response = server.handleAuthenticatedRequest(
                new Request(type, payload), USER_ID
        );
        assertFalse(response.isSuccess());
        assertEquals(message, response.getMessage());
    }

    private static Server server(CourseBotService service) {
        return new Server(0, null, null, null, null, service);
    }

    private static RequestType[] botTypes() {
        return new RequestType[]{
                RequestType.LIST_MY_COURSE_BOTS, RequestType.CREATE_COURSE_BOT,
                RequestType.UPDATE_COURSE_BOT, RequestType.GET_BOT_SOURCES,
                RequestType.ADD_BOT_TEXT_SOURCE, RequestType.UPLOAD_BOT_SOURCE,
                RequestType.ADD_BOT_QUESTION_SOURCES, RequestType.REMOVE_BOT_SOURCE,
                RequestType.GET_BOT_USAGE, RequestType.LIST_MY_AVAILABLE_BOTS,
                RequestType.GET_MY_BOT_HISTORY, RequestType.ASK_COURSE_BOT
        };
    }

    private static final class RecordingService extends CourseBotService {
        final List<RequestType> calls = new ArrayList<>();
        final List<Integer> userIds = new ArrayList<>();
        Object lastPayload;
        String failureMessage;

        private void record(RequestType type, int userId, Object payload) {
            if (failureMessage != null) {
                throw new IllegalStateException(failureMessage);
            }
            calls.add(type);
            userIds.add(userId);
            lastPayload = payload;
        }

        @Override
        public List<CourseBotSummaryDTO> getMyCourseBots(int userId) {
            record(RequestType.LIST_MY_COURSE_BOTS, userId, null);
            return List.of(bot());
        }

        @Override
        public CourseBotSummaryDTO createCourseBot(int userId,
                                                    CreateCourseBotPayload payload) {
            record(RequestType.CREATE_COURSE_BOT, userId, payload);
            return bot();
        }

        @Override
        public CourseBotSummaryDTO updateCourseBot(int userId,
                                                    UpdateCourseBotPayload payload) {
            record(RequestType.UPDATE_COURSE_BOT, userId, payload);
            return bot();
        }

        @Override
        public List<BotSourceDTO> getBotSources(int userId, int botId) {
            record(RequestType.GET_BOT_SOURCES, userId, botId);
            return List.of(source());
        }

        @Override
        public BotSourceDTO addTextSource(int userId, AddBotTextSourcePayload payload) {
            record(RequestType.ADD_BOT_TEXT_SOURCE, userId, payload);
            return source();
        }

        @Override
        public BotSourceDTO uploadSource(int userId, UploadBotSourcePayload payload) {
            record(RequestType.UPLOAD_BOT_SOURCE, userId, payload);
            return source();
        }

        @Override
        public List<BotSourceDTO> addQuestionSources(
                int userId, AddBotQuestionSourcesPayload payload
        ) {
            record(RequestType.ADD_BOT_QUESTION_SOURCES, userId, payload);
            return List.of(source());
        }

        @Override
        public BotSourceDTO removeSource(int userId, RemoveBotSourcePayload payload) {
            record(RequestType.REMOVE_BOT_SOURCE, userId, payload);
            return source();
        }

        @Override
        public BotUsageSummaryDTO getBotUsage(int userId, int botId) {
            record(RequestType.GET_BOT_USAGE, userId, botId);
            return new BotUsageSummaryDTO(7, 1, "Bot", "Course", 0, null, List.of());
        }

        @Override
        public List<CourseBotSummaryDTO> getMyAvailableBots(int userId) {
            record(RequestType.LIST_MY_AVAILABLE_BOTS, userId, null);
            return List.of(bot());
        }

        @Override
        public BotHistoryDTO getMyBotHistory(int userId, int courseId) {
            record(RequestType.GET_MY_BOT_HISTORY, userId, courseId);
            return new BotHistoryDTO(7, 1, "Bot", "Course", List.of());
        }

        @Override
        public BotQuestionResultDTO askCourseBot(int userId, AskCourseBotPayload payload) {
            record(RequestType.ASK_COURSE_BOT, userId, payload);
            return new BotQuestionResultDTO(new BotMessageDTO(
                    1, 1, "Question", "Answer", BotAnswerStatus.ANSWERED, TIME
            ), true);
        }
    }

    private static CourseBotSummaryDTO bot() {
        return new CourseBotSummaryDTO(
                7, 1, "Bot", "Course", BotStatus.ACTIVE, 1, TIME, TIME
        );
    }

    private static BotSourceDTO source() {
        return new BotSourceDTO(
                9, 7, BotSourceType.FREE_TEXT, "Notes", null, null,
                BotSourceStatus.ACTIVE, TIME, null
        );
    }
}
