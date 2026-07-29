package hsts.client.control;

import hsts.common.ExamVersionSelectionPayload;
import hsts.common.ExecutionIdPayload;
import hsts.common.QuestionIdPayload;
import hsts.common.QuestionVersionPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.SubmissionIdPayload;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PrincipalOversightClientControllerTest {
    @Test
    public void listOperationsUseExactReadOnlyRequestMappings() {
        Sender sender = new Sender();
        sender.response = Response.success("ok", List.of());
        PrincipalOversightClientController controller =
                new PrincipalOversightClientController(sender::send);

        assertTrue(controller.getAllQuestions().join().isEmpty());
        assertRequest(sender.last(), RequestType.LIST_ALL_QUESTIONS, null);
        assertTrue(controller.getQuestionVersions(11).join().isEmpty());
        assertEquals(11, ((QuestionIdPayload) sender.last().getPayload()).getQuestionId());
        assertTrue(controller.getAllExams().join().isEmpty());
        assertRequest(sender.last(), RequestType.LIST_ALL_EXAMS, null);
        assertTrue(controller.getExamVersions(5).join().isEmpty());
        assertEquals(5, sender.last().getPayload());
        assertTrue(controller.getAllExecutions().join().isEmpty());
        assertRequest(sender.last(), RequestType.LIST_ALL_EXECUTIONS, null);
        assertTrue(controller.getExecutionResults(7).join().isEmpty());
        assertEquals(7, ((ExecutionIdPayload) sender.last().getPayload()).getExecutionId());
    }

    @Test
    public void detailOperationsCarryOnlyExactObjectAndVersionIdentity() {
        Sender sender = new Sender();
        sender.response = Response.success("ok", "invalid");
        PrincipalOversightClientController controller =
                new PrincipalOversightClientController(sender::send);

        assertThrows(CompletionException.class,
                () -> controller.getQuestionVersion(11, 3).join());
        QuestionVersionPayload question = (QuestionVersionPayload) sender.last().getPayload();
        assertEquals(11, question.getQuestionId());
        assertEquals(3, question.getVersionNo());

        assertThrows(CompletionException.class,
                () -> controller.getExamVersion(5, 2).join());
        ExamVersionSelectionPayload exam =
                (ExamVersionSelectionPayload) sender.last().getPayload();
        assertEquals(5, exam.getExamId());
        assertEquals(2, exam.getVersionNo());

        assertThrows(CompletionException.class,
                () -> controller.getSubmissionResult(9).join());
        assertEquals(9,
                ((SubmissionIdPayload) sender.last().getPayload()).getSubmissionId());
    }

    @Test
    public void nullBlankErrorsAndMalformedListsUseStableSafeMessages() {
        Sender sender = new Sender();
        PrincipalOversightClientController controller =
                new PrincipalOversightClientController(sender::send);
        sender.response = null;
        assertFailure("No response from server", controller::getAllQuestions);
        sender.response = new Response(ResponseStatus.ERROR, " ", null);
        assertFailure("Request failed", controller::getAllExams);
        sender.response = Response.error("Principal denied");
        assertFailure("Principal denied", controller::getAllExecutions);
        sender.response = Response.success("ok", List.of("wrong"));
        assertFailure("Invalid question list response from server",
                controller::getAllQuestions);
    }

    private static void assertFailure(
            String message,
            java.util.function.Supplier<java.util.concurrent.CompletableFuture<?>> call
    ) {
        CompletionException failure = assertThrows(
                CompletionException.class, () -> call.get().join()
        );
        assertEquals(message, failure.getCause().getMessage());
    }

    private static void assertRequest(Request request, RequestType type, Object payload) {
        assertEquals(type, request.getType());
        if (payload == null) assertNull(request.getPayload());
        else assertEquals(payload, request.getPayload());
    }

    private static final class Sender {
        final List<Request> requests = new ArrayList<>();
        Response response;
        Response send(Request request) {
            requests.add(request);
            return response;
        }
        Request last() { return requests.get(requests.size() - 1); }
    }
}
