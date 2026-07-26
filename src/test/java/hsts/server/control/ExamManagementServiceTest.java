package hsts.server.control;

import hsts.common.QuestionDTO;
import hsts.common.UpdateQuestionPayload;
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

    @Test
    public void updateQuestionPersistsEveryFieldAndReturnsMappedDto() {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository(
                question(3, "Old", "Old topic", "EASY", "ACTIVE")
        );
        ExamManagementService service = new ExamManagementService(repository);
        UpdateQuestionPayload payload = new UpdateQuestionPayload(
                3,
                "  Updated content  ",
                "  Calculus  ",
                "  HARD  ",
                "  INACTIVE  ",
                "  images/calculus.png  ",
                "  One  ",
                "  Two  ",
                "  Three  ",
                "  Four  ",
                3
        );

        QuestionDTO result = service.updateQuestion(payload);
        Question stored = repository.getStoredQuestion(3);

        assertEquals(1, repository.getUpdateCalls());
        assertEquals("Updated content", stored.getContent());
        assertEquals("Calculus", stored.getTopic());
        assertEquals("MULTIPLE_CHOICE", stored.getType());
        assertEquals("HARD", stored.getDifficulty());
        assertEquals("INACTIVE", stored.getStatus());
        assertEquals("images/calculus.png", stored.getIllustrationPath());
        assertEquals("One", stored.getAnswerOption1());
        assertEquals("Two", stored.getAnswerOption2());
        assertEquals("Three", stored.getAnswerOption3());
        assertEquals("Four", stored.getAnswerOption4());
        assertEquals(3, stored.getCorrectOptionNumber());
        assertEquals(3, result.getQuestionId());
        assertEquals("Updated content", result.getContent());
        assertEquals("Calculus", result.getTopic());
        assertEquals("MULTIPLE_CHOICE", result.getType());
        assertEquals("HARD", result.getDifficulty());
        assertEquals("INACTIVE", result.getStatus());
        assertEquals("images/calculus.png", result.getIllustrationPath());
        assertEquals("One", result.getAnswerOption1());
        assertEquals("Two", result.getAnswerOption2());
        assertEquals("Three", result.getAnswerOption3());
        assertEquals("Four", result.getAnswerOption4());
        assertEquals(3, result.getCorrectOptionNumber());
    }

    @Test
    public void updateQuestionRereadsTheRepositoryAfterWriting() {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository(
                question(4, "Old", "Topic", "EASY", "ACTIVE")
        );
        ExamManagementService service = new ExamManagementService(repository);

        service.updateQuestion(validPayload(4));

        assertEquals(1, repository.getUpdateCalls());
        assertEquals(1, repository.getFindByIdCalls());
    }

    @Test
    public void updateQuestionRejectsNullPayloadWithExactMessage() {
        ExamManagementService service = new ExamManagementService(new InMemoryQuestionRepository());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateQuestion((UpdateQuestionPayload) null)
        );

        assertEquals("Question update data is missing", exception.getMessage());
    }

    @Test
    public void updateQuestionRejectsBlankContentWithExactMessage() {
        ExamManagementService service = serviceWithQuestion(1);
        UpdateQuestionPayload payload = payload(1, "  ", "Topic", "EASY", "ACTIVE",
                "", "One", "Two", "Three", "Four", 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateQuestion(payload)
        );

        assertEquals("Question content cannot be empty", exception.getMessage());
    }

    @Test
    public void updateQuestionRejectsBlankAnswerOptionWithExactMessage() {
        ExamManagementService service = serviceWithQuestion(1);
        UpdateQuestionPayload payload = payload(1, "Content", "Topic", "EASY", "ACTIVE",
                "", "One", " ", "Three", "Four", 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateQuestion(payload)
        );

        assertEquals("All four answer options are required", exception.getMessage());
    }

    @Test
    public void updateQuestionRejectsCorrectOptionOutsideRangeWithExactMessage() {
        ExamManagementService service = serviceWithQuestion(1);

        for (int correctOption : new int[]{0, 5}) {
            UpdateQuestionPayload payload = payload(1, "Content", "Topic", "EASY", "ACTIVE",
                    "", "One", "Two", "Three", "Four", correctOption);
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.updateQuestion(payload)
            );
            assertEquals("Correct answer number must be between 1 and 4", exception.getMessage());
        }
    }

    @Test
    public void updateQuestionRejectsInvalidStatusWithExactMessage() {
        ExamManagementService service = serviceWithQuestion(1);
        UpdateQuestionPayload payload = payload(1, "Content", "Topic", "EASY", "ARCHIVED",
                "", "One", "Two", "Three", "Four", 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateQuestion(payload)
        );

        assertEquals("Question status must be ACTIVE or INACTIVE", exception.getMessage());
    }

    @Test
    public void updateQuestionAppliesExactNormalizationDefaults() {
        InMemoryQuestionRepository repository = new InMemoryQuestionRepository(
                question(5, "Old", "Old topic", "HARD", "INACTIVE")
        );
        ExamManagementService service = new ExamManagementService(repository);
        UpdateQuestionPayload payload = payload(5, "  Content  ", " ", null, " ", null,
                "  One ", " Two  ", " Three ", " Four ", 2);

        QuestionDTO result = service.updateQuestion(payload);

        assertEquals("Content", result.getContent());
        assertEquals("General", result.getTopic());
        assertEquals("MULTIPLE_CHOICE", result.getType());
        assertEquals("EASY", result.getDifficulty());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("", result.getIllustrationPath());
        assertEquals("One", result.getAnswerOption1());
        assertEquals("Two", result.getAnswerOption2());
        assertEquals("Three", result.getAnswerOption3());
        assertEquals("Four", result.getAnswerOption4());
        assertEquals(2, result.getCorrectOptionNumber());
    }

    private static ExamManagementService serviceWithQuestion(int questionId) {
        return new ExamManagementService(new InMemoryQuestionRepository(
                question(questionId, "Old", "Topic", "EASY", "ACTIVE")
        ));
    }

    private static UpdateQuestionPayload validPayload(int questionId) {
        return payload(questionId, "Content", "Topic", "MEDIUM", "ACTIVE", "",
                "One", "Two", "Three", "Four", 2);
    }

    private static UpdateQuestionPayload payload(int questionId, String content, String topic,
                                                 String difficulty, String status, String illustrationPath,
                                                 String option1, String option2, String option3, String option4,
                                                 int correctOptionNumber) {
        return new UpdateQuestionPayload(
                questionId, content, topic, difficulty, status, illustrationPath,
                option1, option2, option3, option4, correctOptionNumber
        );
    }

    private static Question question(int id, String content, String topic, String difficulty, String status) {
        return new Question(
                id, content, topic, "SAVED_TYPE", difficulty, status, "illustration.png",
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
