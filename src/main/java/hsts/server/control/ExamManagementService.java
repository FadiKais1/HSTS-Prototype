package hsts.server.control;

import hsts.common.QuestionDTO;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.DifficultyLevel;
import hsts.server.entity.Exam;
import hsts.server.entity.Question;
import hsts.server.repository.QuestionRepository;

import java.util.List;

public class ExamManagementService {
    private static final String MULTIPLE_CHOICE = "MULTIPLE_CHOICE";

    private DatabaseService databaseService;
    private final QuestionRepository questionRepository;

    public ExamManagementService(QuestionRepository questionRepository) {
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

    public Question createQuestion(int teacherId, int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateQuestion(int questionId, String content) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
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

    public void deactivateQuestion(int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void activateQuestion(int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void deleteQuestion(int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getActiveQuestionsByCourse(int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getQuestionsByDifficulty(DifficultyLevel difficulty) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getQuestionsByTopic(String topic) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getQuestionsBySubject(int subjectId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void addAnswerOption(int questionId, String optionText, boolean isCorrect) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void removeAnswerOption(int questionId, int optionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public Exam createExam(int teacherId, int courseId, int durationMinutes) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public Exam buildAutomaticExam(int courseId, List topics, DifficultyLevel difficulty, int numberOfQuestions) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void addQuestionToExam(int examId, int questionId, double score) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void removeQuestionFromExam(int examId, int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateQuestionScore(int questionId, double score) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void setExamDuration(int examId, int durationMinutes) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void sendExamForApproval(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void approveExam(int coordinatorId, int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void rejectExam(int coordinatorId, int examId, String reason) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void publishExam(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void editExamNotes(int examId, String teacherNotes, String studentNotes) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void getTopicsByCourse(String courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateExam(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void addExamToDrawer(Object examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
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
