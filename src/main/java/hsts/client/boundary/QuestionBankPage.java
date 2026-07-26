package hsts.client.boundary;

import hsts.client.net.Client;
import hsts.common.type.DifficultyLevel;
import hsts.server.entity.Course;
import hsts.server.entity.Teacher;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class QuestionBankPage extends Application {
    private Client client;
    private Teacher currentTeacher;
    private Course selectedCourse;

    // COMPATIBILITY-ONLY
    private QuestionBankPageController controller;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/hsts/client/boundary/question-bank-page.fxml")
        );

        Scene scene = new Scene(loader.load(), 1280, 820);

        controller = loader.getController();

        stage.setTitle("HSTS Exam Management System - Question Bank Prototype");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (controller != null) {
            controller.close();
        }
    }

    public void showQuestionsByCourse(int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void createQuestion(int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void editQuestion(int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void activateQuestion(int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void deactivateQuestion(int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void filterQuestionsByDifficulty(DifficultyLevel difficulty) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void filterQuestionsByTopic(String topic) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void filterQuestionsBySubject(int subjectId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
