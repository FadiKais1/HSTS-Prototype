package hsts.server.entity;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;

import java.util.List;

public class Student extends User {
    private int studentId;
    // EXTERNAL-DATA: Not present in the users schema and not loaded by UserRepository.
    private String className;
    private String gradeLevel;

    // RELATIONSHIP-DERIVED: Student participates in the many-to-many Course-Student association.
    private List<Course> courses;

    // RELATIONSHIP-DERIVED: Student submits zero or more exam submissions.
    private List<ExamSubmission> examSubmissions;

    private Student(int userId, String fullName, String email, String passwordHash,
                    UserStatus status, List<Notification> notifications,
                    List<Course> courses, List<ExamSubmission> examSubmissions) {
        super(userId, fullName, email, passwordHash, UserRole.STUDENT, status,
                notifications);
        // COMPATIBILITY-ONLY: The diagram's studentId is the users-table account identity.
        this.studentId = userId;
        this.courses = immutableRelationshipCopy(courses, "Student courses are required");
        this.examSubmissions = immutableRelationshipCopy(
                examSubmissions,
                "Student exam submissions are required"
        );
    }

    public static Student rehydrate(int userId, String fullName, String email,
                                    String passwordHash, UserStatus status) {
        return rehydrate(
                userId, fullName, email, passwordHash, status,
                List.of(), List.of(), List.of()
        );
    }

    static Student rehydrate(int userId, String fullName, String email,
                             String passwordHash, UserStatus status,
                             List<Notification> notifications,
                             List<Course> courses,
                             List<ExamSubmission> examSubmissions) {
        return new Student(
                userId, fullName, email, passwordHash, status,
                notifications, courses, examSubmissions
        );
    }

    public int getStudentId() {
        return studentId;
    }

    public List<Course> getCourses() {
        return courses;
    }

    public List<ExamSubmission> getExamSubmissions() {
        return examSubmissions;
    }
}
