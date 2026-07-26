package hsts.client.boundary;

import hsts.client.net.Client;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.User;

public class ExamExecutionPage {
    private Client client;
    private User currentUser;
    private ExamSubmission currentSubmission;
    private int remainingTime;

    public void enterExecutionCode(String code) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void startExam(String StudentId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showRemainingTime() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void submitExam() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showSubMessage() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void extendStudentTime(int minutes, int examId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void selectAnswer(int questionId, int answerNumber) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
