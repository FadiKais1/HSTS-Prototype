package hsts.client.boundary;

import hsts.client.net.Client;
import hsts.common.type.DifficultyLevel;
import hsts.server.entity.Course;
import hsts.server.entity.Teacher;

import java.util.List;

public class ExamBuilderPage {
    private Client client;
    private Teacher currentTeacher;
    private Course selectedCourse;
    private List selectedQuestions;

    public void chooseManualExam(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void selectQuestion(int questionId, double score) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void removeSelectedQuestion(int questionId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void saveExam(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void buildAutomaticExam(int courseId, String topic, DifficultyLevel difficulty,
                                   int numberOfQuestions, double totalScore) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void createExam(int courseId, int duration) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void manageQuestionBank() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void addNotes(String teacherNotes, String studentNotes) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void changeQuestionScore(int questionId, double score) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void chooseAutomaticExam(int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
