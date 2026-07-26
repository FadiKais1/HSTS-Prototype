package hsts.server.control;

import hsts.external.ExternalBotSystem;
import hsts.server.entity.BotMessage;

import java.util.List;

public class CourseBotService {
    private DatabaseService databaseService;
    private ExternalBotSystem externalBotSystem;

    public BotMessage sendQuestionToBot(int studentId, int courseId, String questionText) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List<BotMessage> getBotHistory(int studentId, int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateCourseAccess(int studentId, int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void deactivateBotForStudent(int studentId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void activateBotForStudent(int studentId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
