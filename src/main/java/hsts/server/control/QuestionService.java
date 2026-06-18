package hsts.server.control;

import hsts.common.QuestionDTO;
import hsts.common.UpdateQuestionPayload;
import hsts.server.entity.Question;
import hsts.server.repository.QuestionRepository;

import java.util.List;

public class QuestionService {
    private static final String MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    private final QuestionRepository questionRepository;

    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public List<QuestionDTO> getAllQuestions() {
        return questionRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public QuestionDTO getQuestionById(int questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        return toDto(question);
    }

    public QuestionDTO updateQuestion(int questionId, String content) {
        Question existing = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));

        UpdateQuestionPayload payload = new UpdateQuestionPayload(
                questionId,
                content,
                existing.getTopic(),
                existing.getDifficulty(),
                existing.getStatus(),
                existing.getIllustrationPath(),
                existing.getAnswerOption1(),
                existing.getAnswerOption2(),
                existing.getAnswerOption3(),
                existing.getAnswerOption4(),
                existing.getCorrectOptionNumber()
        );

        return updateQuestion(payload);
    }

    public QuestionDTO updateQuestion(UpdateQuestionPayload payload) {
        validateQuestionPayload(payload);

        Question question = new Question(
                payload.getQuestionId(),
                payload.getContent().trim(),
                normalizeText(payload.getTopic(), "General"),
                MULTIPLE_CHOICE,
                normalizeText(payload.getDifficulty(), "EASY"),
                normalizeText(payload.getStatus(), "ACTIVE"),
                normalizeText(payload.getIllustrationPath(), ""),
                payload.getAnswerOption1().trim(),
                payload.getAnswerOption2().trim(),
                payload.getAnswerOption3().trim(),
                payload.getAnswerOption4().trim(),
                payload.getCorrectOptionNumber()
        );

        boolean updated = questionRepository.updateQuestion(question);
        if (!updated) {
            throw new IllegalArgumentException("Question not found: " + payload.getQuestionId());
        }

        return getQuestionById(payload.getQuestionId());
    }

    private void validateQuestionPayload(UpdateQuestionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Question update data is missing");
        }
        if (isBlank(payload.getContent())) {
            throw new IllegalArgumentException("Question content cannot be empty");
        }
        if (isBlank(payload.getAnswerOption1()) || isBlank(payload.getAnswerOption2()) ||
                isBlank(payload.getAnswerOption3()) || isBlank(payload.getAnswerOption4())) {
            throw new IllegalArgumentException("All four answer options are required");
        }
        if (payload.getCorrectOptionNumber() < 1 || payload.getCorrectOptionNumber() > 4) {
            throw new IllegalArgumentException("Correct answer number must be between 1 and 4");
        }
        String status = normalizeText(payload.getStatus(), "ACTIVE");
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new IllegalArgumentException("Question status must be ACTIVE or INACTIVE");
        }
    }

    private QuestionDTO toDto(Question question) {
        return new QuestionDTO(
                question.getQuestionId(),
                question.getContent(),
                question.getTopic(),
                MULTIPLE_CHOICE,
                question.getDifficulty(),
                question.getStatus(),
                question.getIllustrationPath(),
                question.getAnswerOption1(),
                question.getAnswerOption2(),
                question.getAnswerOption3(),
                question.getAnswerOption4(),
                question.getCorrectOptionNumber()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }
}
