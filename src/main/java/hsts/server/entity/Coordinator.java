package hsts.server.entity;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;

import java.util.List;

public class Coordinator extends Teacher {
    private int coordinatorId;
    // EXTERNAL-DATA: Not present in the users schema and not loaded by UserRepository.
    private String departmentName;

    // RELATIONSHIP-DERIVED: Coordinator approves or rejects zero or more exams.
    private List<Exam> reviewedExams;

    private Coordinator(int userId, String fullName, String email, String passwordHash,
                        UserStatus status, List<Notification> notifications,
                        List<Course> courses, List<Question> questions, List<Exam> exams,
                        List<Exam> reviewedExams) {
        super(userId, fullName, email, passwordHash, UserRole.COORDINATOR, status,
                notifications, courses, questions, exams);
        // COMPATIBILITY-ONLY: The diagram's coordinatorId is the users-table account identity.
        this.coordinatorId = userId;
        this.reviewedExams = immutableRelationshipCopy(
                reviewedExams,
                "Coordinator reviewed exams are required"
        );
    }

    public static Coordinator rehydrate(int userId, String fullName, String email,
                                        String passwordHash, UserStatus status) {
        return rehydrate(
                userId, fullName, email, passwordHash, status,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    static Coordinator rehydrate(int userId, String fullName, String email,
                                 String passwordHash, UserStatus status,
                                 List<Notification> notifications,
                                 List<Course> courses, List<Question> questions,
                                 List<Exam> exams, List<Exam> reviewedExams) {
        return new Coordinator(
                userId, fullName, email, passwordHash, status,
                notifications, courses, questions, exams, reviewedExams
        );
    }

    public int getCoordinatorId() {
        return coordinatorId;
    }

    public List<Exam> getReviewedExams() {
        return reviewedExams;
    }
}
