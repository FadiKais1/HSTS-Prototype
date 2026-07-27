package hsts.client.control;

import hsts.common.CourseSummaryDTO;
import hsts.common.CreateQuestionPayload;
import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.QuestionIdPayload;
import hsts.common.QuestionVersionDTO;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import org.junit.Test;

import java.lang.reflect.Field;
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

public class QuestionClientControllerTest {
    @Test
    public void getMyCoursesSendsNullPayloadAndReturnsTypedDtos() {
        RecordingSender sender = new RecordingSender();
        CourseSummaryDTO course = course();
        sender.setResponse(Response.success("Courses loaded successfully", List.of(course)));
        QuestionClientController controller = new QuestionClientController(sender::send);

        List<CourseSummaryDTO> result = controller.getMyCourses().join();

        Request request = sender.lastRequest();
        assertEquals(RequestType.GET_MY_COURSES, request.getType());
        assertNull(request.getPayload());
        assertEquals(1, result.size());
        assertSame(course, result.get(0));
    }

    @Test
    public void listQuestionsSendsExactFilterAndAcceptsNullFilter() {
        RecordingSender sender = new RecordingSender();
        QuestionDTO question = question(11);
        sender.setResponse(Response.success("Questions loaded successfully", List.of(question)));
        QuestionClientController controller = new QuestionClientController(sender::send);
        QuestionFilterPayload filter = new QuestionFilterPayload(
                7, 3, "algebra", DifficultyLevel.MEDIUM, QuestionStatus.ACTIVE
        );

        List<QuestionDTO> filtered = controller.listQuestions(filter).join();
        Request filteredRequest = sender.lastRequest();
        assertEquals(RequestType.LIST_QUESTIONS, filteredRequest.getType());
        assertSame(filter, filteredRequest.getPayload());
        assertSame(question, filtered.get(0));

        controller.listQuestions(null).join();
        Request unfilteredRequest = sender.lastRequest();
        assertEquals(RequestType.LIST_QUESTIONS, unfilteredRequest.getType());
        assertNull(unfilteredRequest.getPayload());
    }

    @Test
    public void createQuestionSendsExactPayloadAndReturnsQuestion() {
        RecordingSender sender = new RecordingSender();
        QuestionDTO question = question(12);
        sender.setResponse(Response.success("Question created successfully", question));
        QuestionClientController controller = new QuestionClientController(sender::send);
        CreateQuestionPayload payload = createPayload();

        QuestionDTO result = controller.createQuestion(payload).join();

        Request request = sender.lastRequest();
        assertEquals(RequestType.CREATE_QUESTION, request.getType());
        assertSame(payload, request.getPayload());
        assertSame(question, result);
    }

    @Test
    public void versionedUpdateSendsExactPayloadAndReturnsQuestionWithoutUserId() throws Exception {
        RecordingSender sender = new RecordingSender();
        QuestionDTO question = question(14);
        sender.setResponse(Response.success("Question updated successfully", question));
        QuestionClientController controller = new QuestionClientController(sender::send);
        UpdateQuestionPayload payload = updatePayload();

        QuestionDTO result = controller.updateQuestion(payload).join();

        Request request = sender.lastRequest();
        assertEquals(RequestType.UPDATE_QUESTION, request.getType());
        assertSame(payload, request.getPayload());
        assertSame(question, result);
        Field requestUserId = Request.class.getDeclaredField("userId");
        requestUserId.setAccessible(true);
        assertEquals(0, requestUserId.getInt(request));
    }

    @Test
    public void versionedUpdatePreservesServerErrorAndRejectsInvalidSuccessPayload() {
        RecordingSender sender = new RecordingSender();
        QuestionClientController controller = new QuestionClientController(sender::send);

        sender.setResponse(Response.error("Question version conflict"));
        assertFutureError(
                () -> controller.updateQuestion(updatePayload()),
                "Question version conflict"
        );

        sender.setResponse(Response.success("Question updated successfully", null));
        assertFutureError(
                () -> controller.updateQuestion(updatePayload()),
                "Invalid update-question response from server"
        );

        sender.setResponse(Response.success("Question updated successfully", "wrong payload"));
        assertFutureError(
                () -> controller.updateQuestion(updatePayload()),
                "Invalid update-question response from server"
        );
    }

    @Test
    public void statusAndHistoryRequestsWrapOnlyQuestionId() throws Exception {
        RecordingSender sender = new RecordingSender();
        QuestionClientController controller = new QuestionClientController(sender::send);
        QuestionDTO question = question(13);

        sender.setResponse(Response.success("Question activated successfully", question));
        assertSame(question, controller.activateQuestion(71).join());
        assertQuestionIdRequest(sender.lastRequest(), RequestType.ACTIVATE_QUESTION, 71);

        sender.setResponse(Response.success("Question deactivated successfully", question));
        assertSame(question, controller.deactivateQuestion(72).join());
        assertQuestionIdRequest(sender.lastRequest(), RequestType.DEACTIVATE_QUESTION, 72);

        QuestionVersionDTO version = version(73);
        List<QuestionVersionDTO> history = List.of(version);
        sender.setResponse(Response.success("Question history loaded successfully", history));
        List<QuestionVersionDTO> result = controller.getQuestionHistory(73).join();
        assertQuestionIdRequest(sender.lastRequest(), RequestType.GET_QUESTION_HISTORY, 73);
        assertSame(version, result.get(0));

        Field requestUserId = Request.class.getDeclaredField("userId");
        requestUserId.setAccessible(true);
        for (Request request : sender.requests()) {
            assertEquals(0, requestUserId.getInt(request));
        }
    }

    @Test
    public void emptyCourseQuestionAndHistoryListsAreAccepted() {
        RecordingSender sender = new RecordingSender();
        sender.setResponse(Response.success("Loaded", List.of()));
        QuestionClientController controller = new QuestionClientController(sender::send);

        assertEquals(List.of(), controller.getMyCourses().join());
        assertEquals(List.of(), controller.listQuestions(null).join());
        assertEquals(List.of(), controller.getQuestionHistory(1).join());
    }

    @Test
    public void wrongTopLevelPayloadUsesEachExactMessage() {
        RecordingSender sender = new RecordingSender();
        sender.setResponse(Response.success("Success", "wrong payload"));
        QuestionClientController controller = new QuestionClientController(sender::send);

        assertFutureError(
                controller::getMyCourses,
                "Invalid courses response from server"
        );
        assertFutureError(
                () -> controller.listQuestions(null),
                "Invalid question list response from server"
        );
        assertFutureError(
                () -> controller.createQuestion(createPayload()),
                "Invalid create-question response from server"
        );
        assertFutureError(
                () -> controller.activateQuestion(1),
                "Invalid activate-question response from server"
        );
        assertFutureError(
                () -> controller.deactivateQuestion(1),
                "Invalid deactivate-question response from server"
        );
        assertFutureError(
                () -> controller.getQuestionHistory(1),
                "Invalid question-history response from server"
        );
    }

    @Test
    public void nullSuccessfulPayloadUsesEachExactMessage() {
        RecordingSender sender = new RecordingSender();
        sender.setResponse(Response.success("Success", null));
        QuestionClientController controller = new QuestionClientController(sender::send);

        assertFutureError(controller::getMyCourses, "Invalid courses response from server");
        assertFutureError(
                () -> controller.listQuestions(null),
                "Invalid question list response from server"
        );
        assertFutureError(
                () -> controller.createQuestion(createPayload()),
                "Invalid create-question response from server"
        );
        assertFutureError(
                () -> controller.activateQuestion(1),
                "Invalid activate-question response from server"
        );
        assertFutureError(
                () -> controller.deactivateQuestion(1),
                "Invalid deactivate-question response from server"
        );
        assertFutureError(
                () -> controller.getQuestionHistory(1),
                "Invalid question-history response from server"
        );
    }

    @Test
    public void wrongListElementIsRejectedWithoutDiscardingIt() {
        RecordingSender sender = new RecordingSender();
        QuestionClientController controller = new QuestionClientController(sender::send);

        sender.setResponse(Response.success("Loaded", List.of(course(), "wrong")));
        assertFutureError(controller::getMyCourses, "Invalid courses response from server");

        sender.setResponse(Response.success("Loaded", List.of(question(1), "wrong")));
        assertFutureError(
                () -> controller.listQuestions(null),
                "Invalid question list response from server"
        );

        sender.setResponse(Response.success("Loaded", List.of(version(1), "wrong")));
        assertFutureError(
                () -> controller.getQuestionHistory(1),
                "Invalid question-history response from server"
        );
    }

    @Test
    public void serverErrorMessageIsPreserved() {
        RecordingSender sender = new RecordingSender();
        sender.setResponse(Response.error("User is not assigned to course: 7"));
        QuestionClientController controller = new QuestionClientController(sender::send);

        assertFutureError(
                () -> controller.createQuestion(createPayload()),
                "User is not assigned to course: 7"
        );
    }

    @Test
    public void legacyMethodsRetainTheirRequestMappingsAndResults() {
        RecordingSender sender = new RecordingSender();
        QuestionClientController controller = new QuestionClientController(sender::send);
        QuestionDTO question = question(91);

        sender.setResponse(Response.success("Questions loaded successfully", List.of(question)));
        assertSame(question, controller.getAllQuestions().join().get(0));
        assertEquals(RequestType.GET_ALL_QUESTIONS, sender.lastRequest().getType());

        sender.setResponse(Response.success("Question loaded successfully", question));
        assertSame(question, controller.getQuestionById(91).join());
        assertEquals(RequestType.GET_QUESTION_BY_ID, sender.lastRequest().getType());
        assertEquals(91, sender.lastRequest().getPayload());

        sender.setResponse(Response.success("Question updated successfully", question));
        assertSame(question, controller.updateQuestion(question).join());
        assertEquals(RequestType.UPDATE_QUESTION, sender.lastRequest().getType());
        assertEquals(91, ((hsts.common.UpdateQuestionPayload) sender.lastRequest().getPayload())
                .getQuestionId());
    }

    private static void assertQuestionIdRequest(Request request, RequestType type,
                                                int questionId) {
        assertEquals(type, request.getType());
        QuestionIdPayload payload = (QuestionIdPayload) request.getPayload();
        assertEquals(questionId, payload.getQuestionId());
    }

    private static void assertFutureError(Supplier<? extends CompletableFuture<?>> operation,
                                          String expectedMessage) {
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> operation.get().join()
        );
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals(expectedMessage, exception.getCause().getMessage());
    }

    private static CourseSummaryDTO course() {
        return new CourseSummaryDTO(
                7, 3, "MATH-7", "Mathematics", "Mathematics", "7", "2026"
        );
    }

    private static QuestionDTO question(int questionId) {
        return new QuestionDTO(
                questionId, "Content", "Topic", "MULTIPLE_CHOICE", "MEDIUM", "ACTIVE",
                "", "One", "Two", "Three", "Four", 2, 7, 3, 1
        );
    }

    private static CreateQuestionPayload createPayload() {
        return new CreateQuestionPayload(
                7, "Content", "Topic", DifficultyLevel.MEDIUM, "",
                "One", "Two", "Three", "Four", 2
        );
    }

    private static UpdateQuestionPayload updatePayload() {
        return new UpdateQuestionPayload(
                14, "Updated", "Topic", "MEDIUM", "ACTIVE", "",
                "One", "Two", "Three", "Four", 2, 6
        );
    }

    private static QuestionVersionDTO version(int questionId) {
        return new QuestionVersionDTO(
                questionId, 2, 7, "Content", "Topic", QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.MEDIUM, "", "One", "Two", "Three", "Four", 2,
                1002, LocalDateTime.of(2026, 1, 2, 10, 0)
        );
    }

    private static final class RecordingSender {
        private final List<Request> requests = new ArrayList<>();
        private volatile Response response;

        private synchronized Response send(Request request) {
            requests.add(request);
            return response;
        }

        private void setResponse(Response response) {
            this.response = response;
        }

        private synchronized Request lastRequest() {
            return requests.get(requests.size() - 1);
        }

        private synchronized List<Request> requests() {
            return List.copyOf(requests);
        }
    }
}
