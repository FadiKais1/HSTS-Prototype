package hsts.client.control;

import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExecutionCodePayload;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.StartExamPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.SubmissionIdPayload;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;
import org.junit.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionClientControllerTest {
    private static final LocalDateTime OPENING =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime CLOSING = OPENING.plusHours(2);

    @Test
    public void everyOperationSendsExactRequestAndMapsActualServerPayload() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);
        ScheduleExamExecutionPayload schedulePayload =
                new ScheduleExamExecutionPayload(40, 3, OPENING, CLOSING);
        ExamExecutionSummaryDTO summary = summary(81);
        sender.setResponse(Response.success("Scheduled", summary));
        assertSame(summary, controller.scheduleExamExecution(schedulePayload).join());
        assertRequest(sender.lastRequest(), RequestType.SCHEDULE_EXAM_EXECUTION,
                schedulePayload);

        List<ExamExecutionSummaryDTO> summaries = List.of(summary);
        sender.setResponse(Response.success("Loaded", summaries));
        assertEquals(summaries, controller.getMyExamExecutions().join());
        assertRequest(sender.lastRequest(), RequestType.LIST_MY_EXAM_EXECUTIONS, null);

        ExecutionCodePayload codePayload = new ExecutionCodePayload("A1B2");
        ExamExecutionPreviewDTO preview = preview();
        sender.setResponse(Response.success("Validated", preview));
        assertSame(preview, controller.validateExecutionCode(codePayload).join());
        assertRequest(sender.lastRequest(), RequestType.VALIDATE_EXECUTION_CODE,
                codePayload);

        StartExamPayload startPayload = new StartExamPayload(81, "confirmation");
        ExamAttemptDTO attempt = attempt();
        sender.setResponse(Response.success("Started", attempt));
        assertSame(attempt, controller.startExamAttempt(startPayload).join());
        assertRequest(sender.lastRequest(), RequestType.START_EXAM_ATTEMPT, startPayload);

        SubmissionIdPayload activePayload = new SubmissionIdPayload(501);
        sender.setResponse(Response.success("Loaded", attempt));
        assertSame(attempt, controller.getActiveExamAttempt(activePayload).join());
        assertRequest(sender.lastRequest(), RequestType.GET_ACTIVE_EXAM_ATTEMPT,
                activePayload);

        SaveExamAnswerPayload answerPayload =
                new SaveExamAnswerPayload(501, 17, 3);
        StudentAnswerDTO answer = new StudentAnswerDTO(17, 3, OPENING);
        sender.setResponse(Response.success("Saved", answer));
        assertSame(answer, controller.saveExamAnswer(answerPayload).join());
        assertRequest(sender.lastRequest(), RequestType.SAVE_EXAM_ANSWER, answerPayload);

        SubmissionIdPayload submitPayload = new SubmissionIdPayload(501);
        sender.setResponse(Response.success("Submitted", attempt));
        assertSame(attempt, controller.submitExamAttempt(submitPayload).join());
        assertRequest(sender.lastRequest(), RequestType.SUBMIT_EXAM_ATTEMPT,
                submitPayload);

        ExtendSubmissionTimePayload extensionPayload =
                new ExtendSubmissionTimePayload(501, 10, "Approved need");
        sender.setResponse(Response.success("Extended", Boolean.TRUE));
        assertEquals(Boolean.TRUE, controller.extendSubmissionTime(extensionPayload).join());
        assertRequest(sender.lastRequest(), RequestType.EXTEND_SUBMISSION_TIME,
                extensionPayload);
    }

    @Test
    public void executionListsPreserveOrderAndAcceptEmptyResults() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);
        ExamExecutionSummaryDTO first = summary(81);
        ExamExecutionSummaryDTO second = summary(82);
        sender.setResponse(Response.success("Loaded", List.of(first, second)));

        List<ExamExecutionSummaryDTO> ordered = controller.getMyExamExecutions().join();

        assertEquals(List.of(first, second), ordered);
        assertSame(first, ordered.get(0));
        assertSame(second, ordered.get(1));

        sender.setResponse(Response.success("Loaded", List.of()));
        assertEquals(List.of(), controller.getMyExamExecutions().join());
    }

    @Test
    public void malformedExecutionListsAreRejected() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);

        sender.setResponse(Response.success("Loaded", "not a list"));
        assertFutureError(
                controller::getMyExamExecutions,
                "Invalid execution-list response from server"
        );

        sender.setResponse(Response.success("Loaded", List.of(summary(81), "wrong")));
        assertFutureError(
                controller::getMyExamExecutions,
                "Invalid execution-list response from server"
        );
    }

    @Test
    public void everyDistinctWrongResponseTypeUsesSafeFixedMessage() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);
        sender.setResponse(Response.success("Success", "wrong payload"));

        assertFutureError(
                () -> controller.scheduleExamExecution(schedulePayload()),
                "Invalid schedule-execution response from server"
        );
        assertFutureError(
                () -> controller.validateExecutionCode(new ExecutionCodePayload("A1B2")),
                "Invalid execution-code response from server"
        );
        assertFutureError(
                () -> controller.startExamAttempt(new StartExamPayload(81, "confirmation")),
                "Invalid start-attempt response from server"
        );
        assertFutureError(
                () -> controller.getActiveExamAttempt(new SubmissionIdPayload(501)),
                "Invalid active-attempt response from server"
        );
        assertFutureError(
                () -> controller.saveExamAnswer(
                        new SaveExamAnswerPayload(501, 17, 3)
                ),
                "Invalid save-answer response from server"
        );
        assertFutureError(
                () -> controller.submitExamAttempt(new SubmissionIdPayload(501)),
                "Invalid submit-attempt response from server"
        );
        assertFutureError(
                () -> controller.extendSubmissionTime(
                        new ExtendSubmissionTimePayload(501, 10, "Approved need")
                ),
                "Invalid time-extension response from server"
        );
    }

    @Test
    public void nullResponseAndServerErrorsAreHandledSafely() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);

        sender.setResponse(null);
        assertFutureError(controller::getMyExamExecutions, "No response from server");

        sender.setResponse(Response.error("Exam is closed"));
        assertFutureError(
                () -> controller.validateExecutionCode(new ExecutionCodePayload("A1B2")),
                "Exam is closed"
        );

        sender.setResponse(new Response(ResponseStatus.ERROR, "  ", null));
        assertFutureError(
                () -> controller.startExamAttempt(
                        new StartExamPayload(81, "confirmation")
                ),
                "Request failed"
        );

        sender.setResponse(new Response(ResponseStatus.ERROR, null, null));
        assertFutureError(
                () -> controller.submitExamAttempt(new SubmissionIdPayload(501)),
                "Request failed"
        );
    }

    @Test
    public void requestsRunAsynchronouslyAndOnlyInvokeRequestSender() throws Exception {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> senderThread = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExamExecutionClientController controller = new ExamExecutionClientController(request -> {
            senderThread.set(Thread.currentThread());
            entered.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test sender timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test sender interrupted", exception);
            }
            return Response.success("Loaded", List.of());
        });

        CompletableFuture<List<ExamExecutionSummaryDTO>> future =
                controller.getMyExamExecutions();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(future.isDone());
            assertNotSame(callerThread, senderThread.get());
        } finally {
            release.countDown();
        }

        assertEquals(List.of(), future.join());
    }

    @Test
    public void generatedRequestsContainNoAuthenticatedUserIdentity() throws Exception {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);
        sender.setResponse(Response.success("Scheduled", summary(81)));
        controller.scheduleExamExecution(schedulePayload()).join();
        sender.setResponse(Response.success("Loaded", List.of()));
        controller.getMyExamExecutions().join();
        sender.setResponse(Response.success("Validated", preview()));
        controller.validateExecutionCode(new ExecutionCodePayload("A1B2")).join();
        sender.setResponse(Response.success("Started", attempt()));
        controller.startExamAttempt(new StartExamPayload(81, "confirmation")).join();
        sender.setResponse(Response.success("Loaded", attempt()));
        controller.getActiveExamAttempt(new SubmissionIdPayload(501)).join();
        sender.setResponse(Response.success(
                "Saved", new StudentAnswerDTO(17, 3, OPENING)
        ));
        controller.saveExamAnswer(new SaveExamAnswerPayload(501, 17, 3)).join();
        sender.setResponse(Response.success("Submitted", attempt()));
        controller.submitExamAttempt(new SubmissionIdPayload(501)).join();
        sender.setResponse(Response.success("Extended", Boolean.TRUE));
        controller.extendSubmissionTime(
                new ExtendSubmissionTimePayload(501, 10, "Approved need")
        ).join();

        Field requestUserId = Request.class.getDeclaredField("userId");
        requestUserId.setAccessible(true);
        assertEquals(8, sender.requests().size());
        for (Request request : sender.requests()) {
            assertEquals(0, requestUserId.getInt(request));
        }
    }

    @Test
    public void activeAttemptUsesSubmissionIdAndRejectsSuccessfulNullPayload() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);
        SubmissionIdPayload payload = new SubmissionIdPayload(501);
        sender.setResponse(Response.success("Loaded", null));

        assertFutureError(
                () -> controller.getActiveExamAttempt(payload),
                "Invalid active-attempt response from server"
        );
        assertRequest(sender.lastRequest(), RequestType.GET_ACTIVE_EXAM_ATTEMPT, payload);
    }

    private static void assertRequest(Request request, RequestType type,
                                      Object expectedPayload) {
        assertEquals(type, request.getType());
        if (expectedPayload == null) {
            assertNull(request.getPayload());
        } else {
            assertSame(expectedPayload, request.getPayload());
        }
    }

    private static void assertFutureError(
            Supplier<? extends CompletableFuture<?>> operation,
            String expectedMessage
    ) {
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> operation.get().join()
        );
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals(expectedMessage, exception.getCause().getMessage());
    }

    private static ScheduleExamExecutionPayload schedulePayload() {
        return new ScheduleExamExecutionPayload(40, 3, OPENING, CLOSING);
    }

    private static ExamExecutionSummaryDTO summary(int executionId) {
        return new ExamExecutionSummaryDTO(
                executionId, "A1B2", 40, 3, "EX1234", "Approved Midterm",
                7, "Mathematics", OPENING, CLOSING, 75,
                ExecutionStatus.SCHEDULED, 1002, "Teacher", OPENING.minusDays(1),
                0, 0, 0
        );
    }

    private static ExamExecutionPreviewDTO preview() {
        return new ExamExecutionPreviewDTO(
                81, "A1B2", 40, 3, "Approved Midterm", 7, "Mathematics",
                OPENING, CLOSING, 75, ExecutionStatus.SCHEDULED, false
        );
    }

    private static ExamAttemptDTO attempt() {
        return new ExamAttemptDTO(
                501, 81, "A1B2", 40, 3, "Approved Midterm", "Read carefully",
                OPENING, CLOSING, 75, 0, 3600, SubmissionStatus.IN_PROGRESS,
                List.of(), List.of()
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
