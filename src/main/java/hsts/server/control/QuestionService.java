package hsts.server.control;

import hsts.common.QuestionDTO;
import hsts.server.entity.Question;
import hsts.server.repository.QuestionRepository;

import java.util.List;
import java.util.stream.Collectors;

public class QuestionService {
    private final QuestionRepository questionRepository;

    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public List<QuestionDTO> getAllQuestions() {
        return questionRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public QuestionDTO getQuestionById(int questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        return toDto(question);
    }

    public QuestionDTO updateQuestion(int questionId, String content) {
        validateQuestionContent(content);

        boolean updated = questionRepository.updateQuestionContent(questionId, content.trim());
        if (!updated) {
            throw new IllegalArgumentException("Question not found: " + questionId);
        }

        return getQuestionById(questionId);
    }

    private void validateQuestionContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Question content cannot be empty");
        }
    }

    private QuestionDTO toDto(Question question) {
        return new QuestionDTO(
                question.getQuestionId(),
                question.getContent(),
                question.getTopic(),
                question.getType(),
                question.getDifficulty(),
                question.getStatus()
        );
    }
}
