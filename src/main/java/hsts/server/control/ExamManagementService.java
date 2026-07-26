package hsts.server.control;

import hsts.common.type.DifficultyLevel;
import hsts.server.entity.Exam;
import hsts.server.entity.Question;

import java.util.List;

public class ExamManagementService {
    private DatabaseService databaseService;

    public Question createQuestion(int teacherId, int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateQuestion(int questionId, String content) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
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
}
