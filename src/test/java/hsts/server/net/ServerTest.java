package hsts.server.net;

import hsts.common.QuestionDTO;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.UpdateQuestionPayload;
import hsts.server.control.ExamManagementService;
import hsts.server.entity.Question;
import hsts.server.support.InMemoryQuestionRepository;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerTest {
    @Test
    public void getAllQuestionsReturnsExactSuccessResponse() {
        Server server = serverWith(question(1, "First"), question(2, "Second"));

        Response response = server.handleRequest(new Request(RequestType.GET_ALL_QUESTIONS, null));

        assertSuccess(response, "Questions loaded successfully");
        List<?> payload = (List<?>) response.getPayload();
        assertEquals(2, payload.size());
        assertEquals("First", ((QuestionDTO) payload.get(0)).getContent());
        assertEquals("Second", ((QuestionDTO) payload.get(1)).getContent());
    }

    @Test
    public void getQuestionByIdReturnsExactSuccessResponse() {
        Server server = serverWith(question(8, "Requested"));

        Response response = server.handleRequest(new Request(RequestType.GET_QUESTION_BY_ID, 8));

        assertSuccess(response, "Question loaded successfully");
        QuestionDTO payload = (QuestionDTO) response.getPayload();
        assertEquals(8, payload.getQuestionId());
        assertEquals("Requested", payload.getContent());
    }

    @Test
    public void updateQuestionReturnsExactSuccessResponse() {
        Server server = serverWith(question(9, "Old"));
        UpdateQuestionPayload payload = new UpdateQuestionPayload(
                9, "Updated", "Topic", "HARD", "ACTIVE", "",
                "One", "Two", "Three", "Four", 4
        );

        Response response = server.handleRequest(new Request(RequestType.UPDATE_QUESTION, payload));

        assertSuccess(response, "Question updated successfully");
        QuestionDTO result = (QuestionDTO) response.getPayload();
        assertEquals("Updated", result.getContent());
        assertEquals(4, result.getCorrectOptionNumber());
    }

    @Test
    public void serviceExceptionIsConvertedToExactErrorResponse() {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository();
        repository.setFindAllFailure(new IllegalStateException("Repository unavailable"));
        Server server = new Server(0, new ExamManagementService(repository));

        Response response = server.handleRequest(new Request(RequestType.GET_ALL_QUESTIONS, null));

        assertError(response, "Repository unavailable");
    }

    @Test
    public void invalidPayloadIsConvertedToExactErrorResponse() {
        Server server = serverWith(question(10, "Old"));

        Response response = server.handleRequest(new Request(RequestType.UPDATE_QUESTION, null));

        assertError(response, "Question update data is missing");
    }

    private static Server serverWith(Question... questions) {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository(questions);
        return new Server(0, new ExamManagementService(repository));
    }

    private static Question question(int id, String content) {
        return new Question(
                id, content, "Topic", "MULTIPLE_CHOICE", "MEDIUM", "ACTIVE", "",
                "One", "Two", "Three", "Four", 1
        );
    }

    private static void assertSuccess(Response response, String message) {
        assertTrue(response.isSuccess());
        assertEquals(ResponseStatus.SUCCESS, response.getStatus());
        assertEquals(message, response.getMessage());
    }

    private static void assertError(Response response, String message) {
        assertFalse(response.isSuccess());
        assertEquals(ResponseStatus.ERROR, response.getStatus());
        assertEquals(message, response.getMessage());
        assertNull(response.getPayload());
    }
}
