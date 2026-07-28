package hsts.client.control;

import hsts.common.ReportSummaryDTO;
import hsts.common.ReportTargetPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.type.ReportType;
import org.junit.Test;

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

public class ReportClientControllerTest {
    @Test
    public void allOperationsSendExactRequestsAndReturnStrictReportPayload() {
        RecordingSender sender = new RecordingSender();
        ReportClientController controller = new ReportClientController(sender::send);
        ReportSummaryDTO report = report();
        sender.response = Response.success("Loaded", report);

        assertSame(report, controller.getMyAuthoredExamsReport().join());
        assertRequest(sender.last(), RequestType.GET_MY_AUTHORED_EXAMS_REPORT, null);
        assertSame(report, controller.getTeacherExamsReport(1002).join());
        assertTarget(sender.last(), RequestType.GET_TEACHER_EXAMS_REPORT, 1002);
        assertSame(report, controller.getCourseExamsReport(31).join());
        assertTarget(sender.last(), RequestType.GET_COURSE_EXAMS_REPORT, 31);
        assertSame(report, controller.getStudentExamsReport(1001).join());
        assertTarget(sender.last(), RequestType.GET_STUDENT_EXAMS_REPORT, 1001);
        assertSame(report, controller.getExamExecutionReport(81).join());
        assertTarget(sender.last(), RequestType.GET_EXAM_EXECUTION_REPORT, 81);
    }

    @Test
    public void targetValidationHappensBeforeNetworking() {
        RecordingSender sender = new RecordingSender();
        ReportClientController controller = new ReportClientController(sender::send);

        assertFutureError(() -> controller.getTeacherExamsReport(0),
                IllegalArgumentException.class, "Report target ID must be positive");
        assertFutureError(() -> controller.getCourseExamsReport(-1),
                IllegalArgumentException.class, "Report target ID must be positive");
        assertFutureError(() -> controller.getStudentExamsReport(0),
                IllegalArgumentException.class, "Report target ID must be positive");
        assertFutureError(() -> controller.getExamExecutionReport(-9),
                IllegalArgumentException.class, "Report target ID must be positive");
        assertEquals(0, sender.requests.size());
    }

    @Test
    public void nullErrorsAndInvalidSuccessPayloadsUseSafeExactMessages() {
        RecordingSender sender = new RecordingSender();
        ReportClientController controller = new ReportClientController(sender::send);

        sender.response = null;
        assertFutureError(controller::getMyAuthoredExamsReport,
                IllegalStateException.class, "No response from server");
        sender.response = Response.error("Report denied");
        assertFutureError(() -> controller.getCourseExamsReport(31),
                IllegalStateException.class, "Report denied");
        sender.response = new Response(ResponseStatus.ERROR, " ", null);
        assertFutureError(() -> controller.getStudentExamsReport(1001),
                IllegalStateException.class, "Request failed");
        sender.response = Response.success("Loaded", null);
        assertFutureError(() -> controller.getExamExecutionReport(81),
                IllegalStateException.class, "Invalid report response from server");
        sender.response = Response.success("Loaded", "wrong");
        assertFutureError(controller::getMyAuthoredExamsReport,
                IllegalStateException.class, "Invalid report response from server");
    }

    private static void assertRequest(Request request, RequestType type,
                                      Object payload) {
        assertEquals(type, request.getType());
        if (payload == null) {
            assertNull(request.getPayload());
        } else {
            assertSame(payload, request.getPayload());
        }
    }

    private static void assertTarget(Request request, RequestType type, int targetId) {
        assertEquals(type, request.getType());
        ReportTargetPayload payload = (ReportTargetPayload) request.getPayload();
        assertEquals(targetId, payload.getTargetId());
    }

    private static void assertFutureError(
            Supplier<CompletableFuture<?>> operation,
            Class<? extends RuntimeException> type,
            String message
    ) {
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> operation.get().join()
        );
        assertEquals(type, exception.getCause().getClass());
        assertEquals(message, exception.getCause().getMessage());
    }

    private static ReportSummaryDTO report() {
        return new ReportSummaryDTO(
                ReportType.TEACHER_EXAMS,
                "Report",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                1002,
                "Teacher",
                List.of()
        );
    }

    private static final class RecordingSender {
        private final List<Request> requests = new ArrayList<>();
        private Response response;

        private Response send(Request request) {
            requests.add(request);
            return response;
        }

        private Request last() {
            return requests.get(requests.size() - 1);
        }
    }
}
