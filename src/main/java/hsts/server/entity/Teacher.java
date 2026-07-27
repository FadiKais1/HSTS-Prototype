package hsts.server.entity;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;

import java.util.List;

public class Teacher extends User {
    private int teacherId;
    // EXTERNAL-DATA: Not present in the users schema and not loaded by UserRepository.
    private String specialization;

    // RELATIONSHIP-DERIVED: Teacher is assigned to zero or more courses.
    private List<Course> courses;

    // RELATIONSHIP-DERIVED: Teacher creates or updates zero or more questions.
    private List<Question> questions;

    // RELATIONSHIP-DERIVED: Teacher creates zero or more exams.
    private List<Exam> exams;

    private Teacher(int userId, String fullName, String email, String passwordHash,
                    UserStatus status, List<Notification> notifications,
                    List<Course> courses, List<Question> questions, List<Exam> exams) {
        this(userId, fullName, email, passwordHash, UserRole.TEACHER, status,
                notifications, courses, questions, exams);
    }

    protected Teacher(int userId, String fullName, String email, String passwordHash,
                      UserRole role, UserStatus status,
                      List<Notification> notifications, List<Course> courses,
                      List<Question> questions, List<Exam> exams) {
        super(userId, fullName, email, passwordHash, requireTeacherRole(role), status,
                notifications);
        validateRuntimeRole(role);
        // COMPATIBILITY-ONLY: The diagram's teacherId is the users-table account identity.
        this.teacherId = userId;
        this.courses = immutableRelationshipCopy(courses, "Teacher courses are required");
        this.questions = immutableRelationshipCopy(
                questions,
                "Teacher questions are required"
        );
        this.exams = immutableRelationshipCopy(exams, "Teacher exams are required");
    }

    public static Teacher rehydrate(int userId, String fullName, String email,
                                    String passwordHash, UserStatus status) {
        return rehydrate(
                userId, fullName, email, passwordHash, status,
                List.of(), List.of(), List.of(), List.of()
        );
    }

    static Teacher rehydrate(int userId, String fullName, String email,
                             String passwordHash, UserStatus status,
                             List<Notification> notifications,
                             List<Course> courses, List<Question> questions,
                             List<Exam> exams) {
        return new Teacher(
                userId, fullName, email, passwordHash, status,
                notifications, courses, questions, exams
        );
    }

    public int getTeacherId() {
        return teacherId;
    }

    public List<Course> getCourses() {
        return courses;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public List<Exam> getExams() {
        return exams;
    }

    private static UserRole requireTeacherRole(UserRole role) {
        if (role != UserRole.TEACHER && role != UserRole.COORDINATOR) {
            throw new IllegalArgumentException(
                    "Teacher role must be TEACHER or COORDINATOR"
            );
        }
        return role;
    }

    private void validateRuntimeRole(UserRole role) {
        UserRole requiredRole = this instanceof Coordinator
                ? UserRole.COORDINATOR
                : UserRole.TEACHER;
        if (role != requiredRole) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName() + " role must be " + requiredRole
            );
        }
    }
}
