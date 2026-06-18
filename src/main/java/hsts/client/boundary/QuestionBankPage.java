package hsts.client.boundary;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class QuestionBankPage extends Application {
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
}