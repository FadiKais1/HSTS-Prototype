package hsts.client.control;

import hsts.client.net.Client;
import hsts.common.ReportSummaryDTO;
import hsts.common.ReportTargetPayload;
import hsts.common.ReportTargetsDTO;
import hsts.common.ReportExportPayload;
import hsts.common.ReportExportResult;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ReportClientController {
    private static final String REQUEST_FAILED = "Request failed";
    private static final String NULL_RESPONSE = "No response from server";
    private static final String INVALID_RESPONSE =
            "Invalid report response from server";

    private final Client client;
    private final Function<Request, Response> requestSender;

    public ReportClientController(Client client) {
        this.client = Objects.requireNonNull(client, "client");
        this.requestSender = this.client::sendRequest;
    }

    ReportClientController(Function<Request, Response> requestSender) {
        this.client = null;
        this.requestSender = Objects.requireNonNull(requestSender, "requestSender");
    }

    public CompletableFuture<ReportSummaryDTO> getMyAuthoredExamsReport() {
        return sendRequest(new Request(
                RequestType.GET_MY_AUTHORED_EXAMS_REPORT,
                null
        ));
    }

    public CompletableFuture<ReportSummaryDTO> getTeacherExamsReport(
            int teacherUserId
    ) {
        return sendTargetRequest(
                RequestType.GET_TEACHER_EXAMS_REPORT,
                teacherUserId
        );
    }

    public CompletableFuture<ReportSummaryDTO> getCourseExamsReport(int courseId) {
        return sendTargetRequest(RequestType.GET_COURSE_EXAMS_REPORT, courseId);
    }

    public CompletableFuture<ReportSummaryDTO> getStudentExamsReport(
            int studentUserId
    ) {
        return sendTargetRequest(
                RequestType.GET_STUDENT_EXAMS_REPORT,
                studentUserId
        );
    }

    public CompletableFuture<ReportSummaryDTO> getExamExecutionReport(
            int executionId
    ) {
        return sendTargetRequest(
                RequestType.GET_EXAM_EXECUTION_REPORT,
                executionId
        );
    }

    public CompletableFuture<ReportExportResult> exportReport(
            ReportExportPayload payload
    ) {
        if (payload == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Report export data is required")
            );
        }
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(
                    new Request(RequestType.EXPORT_REPORT, payload)
            );
            if (response == null) throw new IllegalStateException(NULL_RESPONSE);
            if (!response.isSuccess()) {
                String message = response.getMessage();
                throw new IllegalStateException(
                        message == null || message.isBlank() ? REQUEST_FAILED : message
                );
            }
            if (!(response.getPayload() instanceof ReportExportResult result)) {
                throw new IllegalStateException("Invalid report export response from server");
            }
            return result;
        });
    }

    private CompletableFuture<ReportSummaryDTO> sendTargetRequest(
            RequestType type,
            int targetId
    ) {
        if (targetId <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Report target ID must be positive")
            );
        }
        return sendRequest(new Request(type, new ReportTargetPayload(targetId)));
    }

    /** The teachers, courses, students and executions the Principal may report on. */
    public CompletableFuture<ReportTargetsDTO> getReportTargets() {
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(
                    new Request(RequestType.LIST_REPORT_TARGETS, null)
            );
            if (response == null) {
                throw new IllegalStateException(NULL_RESPONSE);
            }
            if (!response.isSuccess()) {
                String message = response.getMessage();
                throw new IllegalStateException(
                        message == null || message.isBlank() ? REQUEST_FAILED : message
                );
            }
            if (!(response.getPayload() instanceof ReportTargetsDTO targets)) {
                throw new IllegalStateException(INVALID_RESPONSE);
            }
            return targets;
        });
    }

    private CompletableFuture<ReportSummaryDTO> sendRequest(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(request);
            if (response == null) {
                throw new IllegalStateException(NULL_RESPONSE);
            }
            if (!response.isSuccess()) {
                String message = response.getMessage();
                throw new IllegalStateException(
                        message == null || message.isBlank() ? REQUEST_FAILED : message
                );
            }
            Object payload = response.getPayload();
            if (!(payload instanceof ReportSummaryDTO report)) {
                throw new IllegalStateException(INVALID_RESPONSE);
            }
            return report;
        });
    }
}
