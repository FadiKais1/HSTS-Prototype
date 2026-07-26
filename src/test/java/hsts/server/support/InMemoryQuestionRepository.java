package hsts.server.support;

import hsts.server.entity.Question;
import hsts.server.repository.QuestionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryQuestionRepository extends QuestionRepository {
    private final Map<Integer, Question> questions = new LinkedHashMap<>();
    private RuntimeException findAllFailure;
    private int findAllCalls;
    private int findByIdCalls;
    private int updateCalls;

    public InMemoryQuestionRepository(Question... initialQuestions) {
        for (Question question : initialQuestions) {
            questions.put(question.getQuestionId(), question);
        }
    }

    @Override
    public List<Question> findAll() {
        findAllCalls++;
        if (findAllFailure != null) {
            throw findAllFailure;
        }
        return new ArrayList<>(questions.values());
    }

    @Override
    public Optional<Question> findById(int questionId) {
        findByIdCalls++;
        return Optional.ofNullable(questions.get(questionId));
    }

    @Override
    public boolean updateQuestion(Question question) {
        updateCalls++;
        if (!questions.containsKey(question.getQuestionId())) {
            return false;
        }
        questions.put(question.getQuestionId(), question);
        return true;
    }

    public Question getStoredQuestion(int questionId) {
        return questions.get(questionId);
    }

    public int getFindAllCalls() {
        return findAllCalls;
    }

    public int getFindByIdCalls() {
        return findByIdCalls;
    }

    public int getUpdateCalls() {
        return updateCalls;
    }

    public void setFindAllFailure(RuntimeException findAllFailure) {
        this.findAllFailure = findAllFailure;
    }
}
