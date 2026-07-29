package hsts.client.control;

import hsts.client.net.Client;
import hsts.common.ExamDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.ExamVersionSelectionPayload;
import hsts.common.ExecutionIdPayload;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PrincipalQuestionDTO;
import hsts.common.QuestionIdPayload;
import hsts.common.QuestionVersionPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.SubmissionIdPayload;
import hsts.common.SubmissionReviewDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class PrincipalOversightClientController {
    private static final String NULL_RESPONSE = "No response from server";
    private static final String REQUEST_FAILED = "Request failed";
    private final Function<Request, Response> requestSender;

    public PrincipalOversightClientController(Client client) {
        Objects.requireNonNull(client, "client");
        this.requestSender = client::sendRequest;
    }

    PrincipalOversightClientController(Function<Request, Response> requestSender) {
        this.requestSender = Objects.requireNonNull(requestSender, "requestSender");
    }

    public CompletableFuture<List<PrincipalQuestionDTO>> getAllQuestions() {
        return sendList(RequestType.LIST_ALL_QUESTIONS, null,
                PrincipalQuestionDTO.class, "Invalid question list response from server");
    }

    public CompletableFuture<List<PrincipalQuestionDTO>> getQuestionVersions(
            int questionId
    ) {
        requirePositive(questionId, "Question ID must be positive");
        return sendList(RequestType.LIST_QUESTION_VERSIONS_FOR_PRINCIPAL,
                new QuestionIdPayload(questionId), PrincipalQuestionDTO.class,
                "Invalid question version list response from server");
    }

    public CompletableFuture<PrincipalQuestionDTO> getQuestionVersion(
            int questionId, int versionNo
    ) {
        requirePositive(questionId, "Question ID must be positive");
        requirePositive(versionNo, "Question version must be positive");
        return sendOne(RequestType.GET_QUESTION_VERSION_FOR_PRINCIPAL,
                new QuestionVersionPayload(questionId, versionNo),
                PrincipalQuestionDTO.class,
                "Invalid question response from server");
    }

    public CompletableFuture<List<ExamSummaryDTO>> getAllExams() {
        return sendList(RequestType.LIST_ALL_EXAMS, null, ExamSummaryDTO.class,
                "Invalid exam list response from server");
    }

    public CompletableFuture<List<ExamSummaryDTO>> getExamVersions(int examId) {
        requirePositive(examId, "Exam ID must be positive");
        return sendList(RequestType.LIST_EXAM_VERSIONS_FOR_PRINCIPAL,
                examId, ExamSummaryDTO.class,
                "Invalid exam version list response from server");
    }

    public CompletableFuture<ExamDTO> getExamVersion(int examId, int versionNo) {
        requirePositive(examId, "Exam ID must be positive");
        requirePositive(versionNo, "Exam version must be positive");
        return sendOne(RequestType.GET_EXAM_VERSION_FOR_PRINCIPAL,
                new ExamVersionSelectionPayload(examId, versionNo), ExamDTO.class,
                "Invalid exam response from server");
    }

    public CompletableFuture<List<ExamExecutionSummaryDTO>> getAllExecutions() {
        return sendList(RequestType.LIST_ALL_EXECUTIONS, null,
                ExamExecutionSummaryDTO.class,
                "Invalid execution list response from server");
    }

    public CompletableFuture<List<ExecutionSubmissionSummaryDTO>> getExecutionResults(
            int executionId
    ) {
        requirePositive(executionId, "Execution ID must be positive");
        return sendList(RequestType.LIST_EXECUTION_RESULTS_FOR_PRINCIPAL,
                new ExecutionIdPayload(executionId),
                ExecutionSubmissionSummaryDTO.class,
                "Invalid execution result list response from server");
    }

    public CompletableFuture<SubmissionReviewDTO> getSubmissionResult(int submissionId) {
        requirePositive(submissionId, "Submission ID must be positive");
        return sendOne(RequestType.GET_SUBMISSION_RESULT_FOR_PRINCIPAL,
                new SubmissionIdPayload(submissionId), SubmissionReviewDTO.class,
                "Invalid submission result response from server");
    }

    private <T> CompletableFuture<List<T>> sendList(
            RequestType type, Object payload, Class<T> elementType, String invalidMessage
    ) {
        return send(new Request(type, payload), responsePayload -> {
            if (!(responsePayload instanceof List<?> raw)) {
                throw new IllegalStateException(invalidMessage);
            }
            List<T> result = new ArrayList<>(raw.size());
            for (Object element : raw) {
                if (!elementType.isInstance(element)) {
                    throw new IllegalStateException(invalidMessage);
                }
                result.add(elementType.cast(element));
            }
            return List.copyOf(result);
        });
    }

    private <T> CompletableFuture<T> sendOne(
            RequestType type, Object payload, Class<T> resultType, String invalidMessage
    ) {
        return send(new Request(type, payload), responsePayload -> {
            if (!resultType.isInstance(responsePayload)) {
                throw new IllegalStateException(invalidMessage);
            }
            return resultType.cast(responsePayload);
        });
    }

    private <T> CompletableFuture<T> send(
            Request request, Function<Object, T> mapper
    ) {
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
            return mapper.apply(response.getPayload());
        });
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
