package hsts.server.control;

import hsts.common.QuestionDTO;
import hsts.server.entity.Question;
import hsts.server.support.InMemoryQuestionRepository;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ExamManagementServiceTest {
    @Test
    public void getAllQuestionsMapsEveryQuestionToDto() {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository(
                question(1, "First", "Algebra", "MEDIUM", "ACTIVE"),
                question(2, "Second", "Geometry", "HARD", "INACTIVE")
        );
        ExamManagementService service = new ExamManagementService(repository);

        List<QuestionDTO> result = service.getAllQuestions();

        assertEquals(2, result.size());
        assertQuestionDto(result.get(0), 1, "First", "Algebra", "MEDIUM", "ACTIVE");
        assertQuestionDto(result.get(1), 2, "Second", "Geometry", "HARD", "INACTIVE");
        assertEquals(1, repository.getFindAllCalls());
    }

    @Test
    public void getQuestionByIdReturnsMappedQuestion() {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository(
                question(7, "Existing", "Logic", "EASY", "ACTIVE")
        );
        ExamManagementService service = new ExamManagementService(repository);

        QuestionDTO result = service.getQuestionById(7);

        assertQuestionDto(result, 7, "Existing", "Logic", "EASY", "ACTIVE");
    }

    @Test
    public void getQuestionByIdRejectsMissingQuestionWithExactMessage() {
        ExamManagementService service = new ExamManagementService(new InMemoryQuestionRepository());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getQuestionById(404)
        );

        assertEquals("Question not found: 404", exception.getMessage());
    }

    private static Question question(int id, String content, String topic, String difficulty, String status) {
        return new Question(
                id, content, topic, "MULTIPLE_CHOICE", difficulty, status, "illustration.png",
                "One", "Two", "Three", "Four", 2
        );
    }

    private static void assertQuestionDto(QuestionDTO dto, int id, String content, String topic,
                                          String difficulty, String status) {
        assertEquals(id, dto.getQuestionId());
        assertEquals(content, dto.getContent());
        assertEquals(topic, dto.getTopic());
        assertEquals("MULTIPLE_CHOICE", dto.getType());
        assertEquals(difficulty, dto.getDifficulty());
        assertEquals(status, dto.getStatus());
        assertEquals("illustration.png", dto.getIllustrationPath());
        assertEquals("One", dto.getAnswerOption1());
        assertEquals("Two", dto.getAnswerOption2());
        assertEquals("Three", dto.getAnswerOption3());
        assertEquals("Four", dto.getAnswerOption4());
        assertEquals(2, dto.getCorrectOptionNumber());
    }
}
