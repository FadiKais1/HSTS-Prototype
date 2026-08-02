package hsts.client.control;

import hsts.client.net.Client;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class QuestionClientController {
    private final Function<Request, Response> requestSender;

    public QuestionClientController(Client client) {
        this(Objects.requireNonNull(client, "client")::sendRequest);
    }

    QuestionClientController(Function<Request, Response> requestSender) {
        this.requestSender = Objects.requireNonNull(requestSender, "requestSender");
    }

    public CompletableFuture<List<QuestionDTO>> getAllQuestions() {
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(new Request(RequestType.GET_ALL_QUESTIONS, null));
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return castQuestionList(response.getPayload());
        });
    }

    public CompletableFuture<QuestionDTO> getQuestionById(int questionId) {
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(new Request(RequestType.GET_QUESTION_BY_ID, questionId));
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return (QuestionDTO) response.getPayload();
        });
    }

    public CompletableFuture<QuestionDTO> updateQuestion(int questionId, String content) {
        return CompletableFuture.supplyAsync(() -> {
            UpdateQuestionPayload payload = new UpdateQuestionPayload(questionId, content);
            Response response = requestSender.apply(new Request(RequestType.UPDATE_QUESTION, payload));
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return (QuestionDTO) response.getPayload();
        });
    }

    public CompletableFuture<QuestionDTO> updateQuestion(QuestionDTO question) {
        return CompletableFuture.supplyAsync(() -> {
            UpdateQuestionPayload payload = new UpdateQuestionPayload(
                    question.getQuestionId(),
                    question.getContent(),
                    question.getTopic(),
                    question.getDifficulty(),
                    question.getStatus(),
                    "",
                    question.getAnswerOption1(),
                    question.getAnswerOption2(),
                    question.getAnswerOption3(),
                    question.getAnswerOption4(),
                    question.getCorrectOptionNumber()
            );

            Response response = requestSender.apply(new Request(RequestType.UPDATE_QUESTION, payload));
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return (QuestionDTO) response.getPayload();
        });
    }

    public CompletableFuture<List<CourseSummaryDTO>> getMyCourses() {
        return sendRequest(
                new Request(RequestType.GET_MY_COURSES, null),
                payload -> requireListPayload(
                        payload,
                        CourseSummaryDTO.class,
                        "Invalid courses response from server"
                )
        );
    }

    public CompletableFuture<List<QuestionDTO>> listQuestions(QuestionFilterPayload filter) {
        return sendRequest(
                new Request(RequestType.LIST_QUESTIONS, filter),
                payload -> requireListPayload(
                        payload,
                        QuestionDTO.class,
                        "Invalid question list response from server"
                )
        );
    }

    public CompletableFuture<QuestionDTO> createQuestion(CreateQuestionPayload payload) {
        return sendRequest(
                new Request(RequestType.CREATE_QUESTION, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        QuestionDTO.class,
                        "Invalid create-question response from server"
                )
        );
    }

    public CompletableFuture<QuestionDTO> updateQuestion(UpdateQuestionPayload payload) {
        return sendRequest(
                new Request(RequestType.UPDATE_QUESTION, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        QuestionDTO.class,
                        "Invalid update-question response from server"
                )
        );
    }

    public CompletableFuture<QuestionDTO> activateQuestion(int questionId) {
        return sendRequest(
                new Request(
                        RequestType.ACTIVATE_QUESTION,
                        new QuestionIdPayload(questionId)
                ),
                payload -> requirePayload(
                        payload,
                        QuestionDTO.class,
                        "Invalid activate-question response from server"
                )
        );
    }

    /**
     * Hides a question from the bank. Exams already containing it keep it, so
     * the server returns no question payload.
     */
    public CompletableFuture<Void> deleteQuestion(int questionId) {
        return sendRequest(
                new Request(
                        RequestType.DELETE_QUESTION,
                        new QuestionIdPayload(questionId)
                ),
                payload -> null
        );
    }

    public CompletableFuture<QuestionDTO> deactivateQuestion(int questionId) {
        return sendRequest(
                new Request(
                        RequestType.DEACTIVATE_QUESTION,
                        new QuestionIdPayload(questionId)
                ),
                payload -> requirePayload(
                        payload,
                        QuestionDTO.class,
                        "Invalid deactivate-question response from server"
                )
        );
    }

    public CompletableFuture<List<QuestionVersionDTO>> getQuestionHistory(int questionId) {
        return sendRequest(
                new Request(
                        RequestType.GET_QUESTION_HISTORY,
                        new QuestionIdPayload(questionId)
                ),
                payload -> requireListPayload(
                        payload,
                        QuestionVersionDTO.class,
                        "Invalid question-history response from server"
                )
        );
    }

    private <T> CompletableFuture<T> sendRequest(Request request,
                                                  Function<Object, T> payloadMapper) {
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(request);
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return payloadMapper.apply(response.getPayload());
        });
    }

    private <T> T requirePayload(Object payload, Class<T> payloadType,
                                 String invalidPayloadMessage) {
        if (!payloadType.isInstance(payload)) {
            throw new IllegalStateException(invalidPayloadMessage);
        }
        return payloadType.cast(payload);
    }

    private <T> List<T> requireListPayload(Object payload, Class<T> elementType,
                                           String invalidPayloadMessage) {
        if (!(payload instanceof List<?> rawList)) {
            throw new IllegalStateException(invalidPayloadMessage);
        }

        List<T> typedList = new ArrayList<>(rawList.size());
        for (Object element : rawList) {
            if (!elementType.isInstance(element)) {
                throw new IllegalStateException(invalidPayloadMessage);
            }
            typedList.add(elementType.cast(element));
        }
        return List.copyOf(typedList);
    }

    @SuppressWarnings("unchecked")
    private List<QuestionDTO> castQuestionList(Object payload) {
        return (List<QuestionDTO>) payload;
    }
}
