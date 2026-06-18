package hsts.client.control;

import hsts.client.net.HSTSClient;
import hsts.common.QuestionDTO;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.UpdateQuestionPayload;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class QuestionClientController {
    private final HSTSClient client;

    public QuestionClientController(HSTSClient client) {
        this.client = client;
    }

    public CompletableFuture<List<QuestionDTO>> getAllQuestions() {
        return CompletableFuture.supplyAsync(() -> {
            Response response = client.sendRequest(new Request(RequestType.GET_ALL_QUESTIONS, null));
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return castQuestionList(response.getPayload());
        });
    }

    public CompletableFuture<QuestionDTO> getQuestionById(int questionId) {
        return CompletableFuture.supplyAsync(() -> {
            Response response = client.sendRequest(new Request(RequestType.GET_QUESTION_BY_ID, questionId));
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return (QuestionDTO) response.getPayload();
        });
    }

    public CompletableFuture<QuestionDTO> updateQuestion(int questionId, String content) {
        return CompletableFuture.supplyAsync(() -> {
            UpdateQuestionPayload payload = new UpdateQuestionPayload(questionId, content);
            Response response = client.sendRequest(new Request(RequestType.UPDATE_QUESTION, payload));
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
                    question.getIllustrationPath(),
                    question.getAnswerOption1(),
                    question.getAnswerOption2(),
                    question.getAnswerOption3(),
                    question.getAnswerOption4(),
                    question.getCorrectOptionNumber()
            );

            Response response = client.sendRequest(new Request(RequestType.UPDATE_QUESTION, payload));
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return (QuestionDTO) response.getPayload();
        });
    }

    @SuppressWarnings("unchecked")
    private List<QuestionDTO> castQuestionList(Object payload) {
        return (List<QuestionDTO>) payload;
    }
}
