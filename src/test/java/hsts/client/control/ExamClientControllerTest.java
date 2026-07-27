package hsts.client.control;

import hsts.common.CreateExamPayload;
import hsts.common.ExamDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamSummaryDTO;
import hsts.common.ExamVersionPayload;
import hsts.common.RejectExamPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.UpdateExamPayload;
import hsts.common.type.ExamStatus;
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

public class ExamClientControllerTest {
    @Test
    public void everyMethodSendsExactRequestAndReturnsExactPayloadWithoutIdentity()
            throws Exception {
        RecordingSender sender = new RecordingSender();
        ExamClientController controller = new ExamClientController(sender::send);
        ExamSummaryDTO teacherSummary = summary(11);
        ExamSummaryDTO pendingSummary = summary(12);
        ExamDTO teacherExam = exam(11);
        ExamDTO createdExam = exam(13);
        ExamDTO pendingExam = exam(12);
        CreateExamPayload createPayload = createPayload();

        sender.setResponse(Response.success("Exams loaded successfully", List.of(teacherSummary)));
        assertSame(teacherSummary, controller.getMyExams().join().get(0));
        assertRequest(sender.lastRequest(), RequestType.LIST_MY_EXAMS, null);

        sender.setResponse(Response.success("Exam loaded successfully", teacherExam));
        assertSame(teacherExam, controller.getMyExam(11).join());
        assertRequest(sender.lastRequest(), RequestType.GET_MY_EXAM, 11);

        sender.setResponse(Response.success("Exam created successfully", createdExam));
        assertSame(createdExam, controller.createExam(createPayload).join());
        assertRequest(sender.lastRequest(), RequestType.CREATE_EXAM, createPayload);

        sender.setResponse(Response.success(
                "Pending exams loaded successfully",
                List.of(pendingSummary)
        ));
        assertSame(pendingSummary, controller.getPendingExams().join().get(0));
        assertRequest(sender.lastRequest(), RequestType.LIST_PENDING_EXAMS, null);

        sender.setResponse(Response.success("Pending exam loaded successfully", pendingExam));
        assertSame(pendingExam, controller.getPendingExam(12).join());
        assertRequest(sender.lastRequest(), RequestType.GET_PENDING_EXAM, 12);

        Field requestUserId = Request.class.getDeclaredField("userId");
        requestUserId.setAccessible(true);
        for (Request request : sender.requests()) {
            assertEquals(0, requestUserId.getInt(request));
        }
        assertEquals(5, sender.getSendCalls());
    }

    @Test
    public void workflowMethodsSendExactRequestsAndReturnExactExamWithoutIdentity()
            throws Exception {
        RecordingSender sender = new RecordingSender();
        ExamClientController controller = new ExamClientController(sender::send);
        ExamDTO result = exam(31);
        UpdateExamPayload updatePayload = updatePayload();
        ExamVersionPayload submitPayload = new ExamVersionPayload(31, 2);
        ExamVersionPayload approvePayload = new ExamVersionPayload(31, 2);
        RejectExamPayload rejectPayload = new RejectExamPayload(
                31,
                2,
                "Keep this reason unchanged"
        );
        sender.setResponse(Response.success("Success", result));

        assertSame(result, controller.updateExam(updatePayload).join());
        assertRequest(sender.lastRequest(), RequestType.UPDATE_EXAM, updatePayload);

        assertSame(result, controller.submitExamForApproval(submitPayload).join());
        assertRequest(
                sender.lastRequest(),
                RequestType.SUBMIT_EXAM_FOR_APPROVAL,
                submitPayload
        );

        assertSame(result, controller.approveExam(approvePayload).join());
        assertRequest(sender.lastRequest(), RequestType.APPROVE_EXAM, approvePayload);

        assertSame(result, controller.rejectExam(rejectPayload).join());
        assertRequest(sender.lastRequest(), RequestType.REJECT_EXAM, rejectPayload);

        assertEquals(2, updatePayload.getExpectedVersionNo());
        assertEquals(2, submitPayload.getExpectedVersionNo());
        assertEquals(2, approvePayload.getExpectedVersionNo());
        assertEquals(2, rejectPayload.getExpectedVersionNo());
        assertEquals("Keep this reason unchanged", rejectPayload.getReason());
        assertEquals(4, sender.getSendCalls());

        Field requestUserId = Request.class.getDeclaredField("userId");
        requestUserId.setAccessible(true);
        for (Request request : sender.requests()) {
            assertEquals(0, requestUserId.getInt(request));
        }
    }

    @Test
    public void workflowMethodsRejectNullAndWrongSuccessfulPayloadsWithExactMessages() {
        RecordingSender sender = new RecordingSender();
        ExamClientController controller = new ExamClientController(sender::send);

        for (Object invalidPayload : new Object[]{"wrong payload", null}) {
            sender.setResponse(Response.success("Success", invalidPayload));
            assertFutureError(
                    () -> controller.updateExam(updatePayload()),
                    "Invalid update-exam response from server"
            );
            assertFutureError(
                    () -> controller.submitExamForApproval(new ExamVersionPayload(31, 2)),
                    "Invalid submit-exam response from server"
            );
            assertFutureError(
                    () -> controller.approveExam(new ExamVersionPayload(31, 2)),
                    "Invalid approve-exam response from server"
            );
            assertFutureError(
                    () -> controller.rejectExam(
                            new RejectExamPayload(31, 2, "Reason")
                    ),
                    "Invalid reject-exam response from server"
            );
        }
    }

    @Test
    public void workflowMethodsPreserveExactServerErrorsAndSubmitOnlyOnce() {
        RecordingSender sender = new RecordingSender();
        ExamClientController controller = new ExamClientController(sender::send);
        sender.setResponse(Response.error("Exam version conflict"));

        assertFutureError(
                () -> controller.updateExam(updatePayload()),
                "Exam version conflict"
        );
        assertFutureError(
                () -> controller.submitExamForApproval(new ExamVersionPayload(31, 2)),
                "Exam version conflict"
        );
        assertFutureError(
                () -> controller.approveExam(new ExamVersionPayload(31, 2)),
                "Exam version conflict"
        );
        assertFutureError(
                () -> controller.rejectExam(new RejectExamPayload(31, 2, "Reason")),
                "Exam version conflict"
        );

        assertEquals(4, sender.getSendCalls());
        assertEquals(RequestType.SUBMIT_EXAM_FOR_APPROVAL,
                sender.requests().get(1).getType());
    }

    @Test
    public void listResponsesPreserveOrderAcceptEmptyAndReturnSafeTypedCopies() {
        RecordingSender sender = new RecordingSender();
        ExamClientController controller = new ExamClientController(sender::send);
        ExamSummaryDTO second = summary(2);
        ExamSummaryDTO first = summary(1);
        List<ExamSummaryDTO> serverOrder = new ArrayList<>(List.of(second, first));

        sender.setResponse(Response.success("Loaded", serverOrder));
        List<ExamSummaryDTO> result = controller.getMyExams().join();
        assertEquals(2, result.size());
        assertSame(second, result.get(0));
        assertSame(first, result.get(1));
        assertNotSame(serverOrder, result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(summary(3)));

        sender.setResponse(Response.success("Loaded", List.of()));
        assertEquals(List.of(), controller.getMyExams().join());
        assertEquals(List.of(), controller.getPendingExams().join());
    }

    @Test
    public void wrongOrNullSuccessfulPayloadUsesEveryExactRequiredMessage() {
        RecordingSender sender = new RecordingSender();
        ExamClientController controller = new ExamClientController(sender::send);

        for (Object invalidPayload : new Object[]{"wrong payload", null}) {
            sender.setResponse(Response.success("Success", invalidPayload));
            assertFutureError(
                    controller::getMyExams,
                    "Invalid exam list response from server"
            );
            assertFutureError(
                    () -> controller.getMyExam(1),
                    "Invalid exam response from server"
            );
            assertFutureError(
                    () -> controller.createExam(createPayload()),
                    "Invalid create-exam response from server"
            );
            assertFutureError(
                    controller::getPendingExams,
                    "Invalid pending-exam list response from server"
            );
            assertFutureError(
                    () -> controller.getPendingExam(1),
                    "Invalid pending-exam response from server"
            );
        }
    }

    @Test
    public void invalidElementInEitherListIsRejectedWithExactMessage() {
        RecordingSender sender = new RecordingSender();
        ExamClientController controller = new ExamClientController(sender::send);

        sender.setResponse(Response.success("Loaded", List.of(summary(1), "wrong")));
        assertFutureError(
                controller::getMyExams,
                "Invalid exam list response from server"
        );

        sender.setResponse(Response.success("Loaded", List.of(summary(1), "wrong")));
        assertFutureError(
                controller::getPendingExams,
                "Invalid pending-exam list response from server"
        );
    }

    @Test
    public void everyMethodPreservesExactServerErrorMessage() {
        RecordingSender sender = new RecordingSender();
        sender.setResponse(Response.error("Only coordinators can review exams"));
        ExamClientController controller = new ExamClientController(sender::send);

        assertFutureError(controller::getMyExams, "Only coordinators can review exams");
        assertFutureError(() -> controller.getMyExam(1),
                "Only coordinators can review exams");
        assertFutureError(() -> controller.createExam(createPayload()),
                "Only coordinators can review exams");
        assertFutureError(controller::getPendingExams,
                "Only coordinators can review exams");
        assertFutureError(() -> controller.getPendingExam(1),
                "Only coordinators can review exams");
    }

    @Test
    public void requestSenderExceptionPropagatesUnchangedThroughFuture() {
        IllegalStateException failure = new IllegalStateException("Network failure");
        ExamClientController controller = new ExamClientController(request -> {
            throw failure;
        });

        CompletionException result = assertThrows(
                CompletionException.class,
                () -> controller.getMyExams().join()
        );

        assertSame(failure, result.getCause());
    }

    @Test
    public void requestExecutionIsAsynchronousAndUsesTheSameSender() throws Exception {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> senderThread = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExamClientController controller = new ExamClientController(request -> {
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

        CompletableFuture<List<ExamSummaryDTO>> future = controller.getMyExams();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(future.isDone());
            assertNotSame(callerThread, senderThread.get());
        } finally {
            release.countDown();
        }

        assertEquals(List.of(), future.join());
    }

    private static void assertRequest(Request request, RequestType type,
                                      Object expectedPayload) {
        assertEquals(type, request.getType());
        if (expectedPayload == null) {
            assertNull(request.getPayload());
        } else if (expectedPayload instanceof Integer) {
            assertEquals(expectedPayload, request.getPayload());
        } else {
            assertSame(expectedPayload, request.getPayload());
        }
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

    private static CreateExamPayload createPayload() {
        return new CreateExamPayload(
                7,
                "Midterm",
                60,
                "",
                "Read carefully",
                List.of(new ExamQuestionSelectionPayload(41, 2, 1, 100))
        );
    }

    private static UpdateExamPayload updatePayload() {
        return new UpdateExamPayload(
                31,
                2,
                "Revised exam",
                75,
                "Teacher notes",
                "Read carefully",
                List.of(new ExamQuestionSelectionPayload(41, 3, 1, 100))
        );
    }

    private static ExamSummaryDTO summary(int examId) {
        return new ExamSummaryDTO(
                examId, "ABC123", 7, "Course", 3, "Subject", 1002,
                "Creator", 1, "Exam", 60, 100, ExamStatus.DRAFT,
                LocalDateTime.of(2026, 7, 1, 10, 0), null, null, null
        );
    }

    private static ExamDTO exam(int examId) {
        return new ExamDTO(
                examId, "ABC123", 7, "Course", 3, "Subject", 1002,
                "Creator", 1, "Exam", 60, "", "Read carefully", 100,
                ExamStatus.DRAFT, LocalDateTime.of(2026, 7, 1, 10, 0),
                null, null, null, null, null, List.of()
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

        private synchronized int getSendCalls() {
            return requests.size();
        }
    }
}
