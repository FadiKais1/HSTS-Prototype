package hsts.server.control;

import hsts.server.entity.AuditLog;
import hsts.server.entity.BotMessage;
import hsts.server.entity.Course;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamExecution;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.Notification;
import hsts.server.entity.Question;
import hsts.server.entity.Report;
import hsts.server.entity.User;
import hsts.server.entity.executionCode;
import hsts.server.repository.DatabaseConnection;

import java.util.List;

public class DatabaseService {
    private DatabaseConnection connection;

    public User findUserByEmail(String emailId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public User findUserById(int userId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public Course findCourseById(int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List findQuestionsByCourse(int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveQuestion(Question question) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public Exam findExamById(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveExam(Exam exam) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public executionCode findExecutionCode(int executionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveExecution(ExamExecution execution) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public ExamSubmission findSubmissionById(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveSubmission(ExamSubmission submission) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List findSubmissionsByStudent(int studentId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List findSubmissionsByExam(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveReport(Report report) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveNotification(Notification notification) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List findNotificationsByUser(int userId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveAuditLogs(AuditLog log) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveBotMessage(BotMessage message) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List<BotMessage> findBotHistory(int studentId, int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void findTopicsByCourse(String courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
