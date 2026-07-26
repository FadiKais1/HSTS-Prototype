package hsts.client.boundary;

import hsts.client.net.Client;
import hsts.server.entity.Course;
import hsts.server.entity.Student;

import java.util.List;

public class CourseBotPage {
    private Client client;
    private Student currentStudent;
    private Course selectedCourse;
    private List messages;
    private String questionInput;

    public void showBotHistory(int courseId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void sendQuestionToBot(int courseId, String questionText) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void displayBotAnswer(String answerText) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
