package hsts.server.entity;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UserHierarchyTest {
    @Test
    public void subtypeFactoriesFixRoleAndMapAccountIdentity() {
        Student student = Student.rehydrate(
                101, "Student", "student@hsts.local", "student-hash", UserStatus.ACTIVE
        );
        Teacher teacher = Teacher.rehydrate(
                102, "Teacher", "teacher@hsts.local", "teacher-hash", UserStatus.ACTIVE
        );
        Coordinator coordinator = Coordinator.rehydrate(
                103, "Coordinator", "coordinator@hsts.local", "coordinator-hash",
                UserStatus.ACTIVE
        );
        Principal principal = Principal.rehydrate(
                104, "Principal", "principal@hsts.local", "principal-hash",
                UserStatus.ACTIVE
        );

        assertTrue(student instanceof User);
        assertEquals(UserRole.STUDENT, student.getRole());
        assertEquals(student.getUserId(), student.getStudentId());

        assertTrue(teacher instanceof User);
        assertEquals(UserRole.TEACHER, teacher.getRole());
        assertEquals(teacher.getUserId(), teacher.getTeacherId());

        assertTrue(coordinator instanceof Teacher);
        assertEquals(UserRole.COORDINATOR, coordinator.getRole());
        assertEquals(coordinator.getUserId(), coordinator.getTeacherId());
        assertEquals(coordinator.getUserId(), coordinator.getCoordinatorId());

        assertTrue(principal instanceof User);
        assertEquals(UserRole.PRINCIPAL, principal.getRole());
        assertEquals(principal.getUserId(), principal.getPrincipalId());
    }

    @Test
    public void basePersistedFieldsAndStatusRemainExactForEverySubtype() {
        List<User> users = List.of(
                Student.rehydrate(201, "Student", "s@hsts.local", "s-hash",
                        UserStatus.ACTIVE),
                Teacher.rehydrate(202, "Teacher", "t@hsts.local", "t-hash",
                        UserStatus.BLOCKED),
                Coordinator.rehydrate(203, "Coordinator", "c@hsts.local", "c-hash",
                        UserStatus.ACTIVE),
                Principal.rehydrate(204, "Principal", "p@hsts.local", "p-hash",
                        UserStatus.BLOCKED)
        );

        assertUser(users.get(0), 201, "Student", "s@hsts.local", "s-hash",
                UserStatus.ACTIVE, true);
        assertUser(users.get(1), 202, "Teacher", "t@hsts.local", "t-hash",
                UserStatus.BLOCKED, false);
        assertUser(users.get(2), 203, "Coordinator", "c@hsts.local", "c-hash",
                UserStatus.ACTIVE, true);
        assertUser(users.get(3), 204, "Principal", "p@hsts.local", "p-hash",
                UserStatus.BLOCKED, false);
    }

    @Test
    public void relationshipSnapshotsAreDefensiveOrderedAndImmutable() {
        Notification notification = new Notification();
        Course course = new Course();
        ExamSubmission submission = new ExamSubmission();
        Question question = question();
        Exam exam = new Exam();
        Report report = new Report();

        List<Notification> notifications = new ArrayList<>(List.of(notification));
        List<Course> courses = new ArrayList<>(List.of(course));
        List<ExamSubmission> submissions = new ArrayList<>(List.of(submission));
        List<Question> questions = new ArrayList<>(List.of(question));
        List<Exam> exams = new ArrayList<>(List.of(exam));
        List<Exam> reviewedExams = new ArrayList<>(List.of(exam));
        List<Report> reports = new ArrayList<>(List.of(report));

        Student student = Student.rehydrate(
                301, "Student", "s@hsts.local", "hash", UserStatus.ACTIVE,
                notifications, courses, submissions
        );
        Coordinator coordinator = Coordinator.rehydrate(
                302, "Coordinator", "c@hsts.local", "hash", UserStatus.ACTIVE,
                notifications, courses, questions, exams, reviewedExams
        );
        Principal principal = Principal.rehydrate(
                303, "Principal", "p@hsts.local", "hash", UserStatus.ACTIVE,
                notifications, reports
        );

        notifications.clear();
        courses.clear();
        submissions.clear();
        questions.clear();
        exams.clear();
        reviewedExams.clear();
        reports.clear();

        assertSame(notification, student.getNotifications().get(0));
        assertSame(course, student.getCourses().get(0));
        assertSame(submission, student.getExamSubmissions().get(0));
        assertSame(question, coordinator.getQuestions().get(0));
        assertSame(exam, coordinator.getExams().get(0));
        assertSame(exam, coordinator.getReviewedExams().get(0));
        assertSame(report, principal.getReports().get(0));

        assertThrows(UnsupportedOperationException.class,
                () -> student.getNotifications().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> student.getCourses().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> student.getExamSubmissions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> coordinator.getCourses().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> coordinator.getQuestions().add(question));
        assertThrows(UnsupportedOperationException.class,
                () -> coordinator.getExams().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> coordinator.getReviewedExams().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> principal.getNotifications().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> principal.getReports().clear());
    }

    @Test
    public void unloadedRelationshipsAreSafeAndNullElementsAreRejected() {
        Student student = Student.rehydrate(
                401, "Student", "s@hsts.local", "hash", UserStatus.ACTIVE
        );
        Teacher teacher = Teacher.rehydrate(
                402, "Teacher", "t@hsts.local", "hash", UserStatus.ACTIVE
        );
        Coordinator coordinator = Coordinator.rehydrate(
                403, "Coordinator", "c@hsts.local", "hash", UserStatus.ACTIVE
        );
        Principal principal = Principal.rehydrate(
                404, "Principal", "p@hsts.local", "hash", UserStatus.ACTIVE
        );

        assertTrue(student.getNotifications().isEmpty());
        assertTrue(student.getCourses().isEmpty());
        assertTrue(student.getExamSubmissions().isEmpty());
        assertTrue(teacher.getCourses().isEmpty());
        assertTrue(teacher.getQuestions().isEmpty());
        assertTrue(teacher.getExams().isEmpty());
        assertTrue(coordinator.getReviewedExams().isEmpty());
        assertTrue(principal.getReports().isEmpty());

        List<Course> invalidCourses = new ArrayList<>();
        invalidCourses.add(null);
        assertThrows(
                NullPointerException.class,
                () -> Student.rehydrate(
                        405, "Student", "s2@hsts.local", "hash", UserStatus.ACTIVE,
                        List.of(), invalidCourses, List.of()
                )
        );
    }

    @Test
    public void contradictoryTeacherSubtypeRoleIsRejected() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                ContradictoryTeacher::new
        );

        assertEquals("ContradictoryTeacher role must be TEACHER", failure.getMessage());
    }

    @Test
    public void profileMutationIsExternallyManagedAndToStringDoesNotExposeCredentials()
            throws Exception {
        List<User> users = List.of(
                Student.rehydrate(
                        501, "Student", "s@hsts.local", "student-secret-hash",
                        UserStatus.ACTIVE
                ),
                Teacher.rehydrate(
                        502, "Teacher", "t@hsts.local", "teacher-secret-hash",
                        UserStatus.ACTIVE
                ),
                Coordinator.rehydrate(
                        503, "Coordinator", "c@hsts.local", "coordinator-secret-hash",
                        UserStatus.ACTIVE
                ),
                Principal.rehydrate(
                        504, "Principal", "p@hsts.local", "principal-secret-hash",
                        UserStatus.ACTIVE
                )
        );
        User student = users.get(0);

        UnsupportedOperationException failure = assertThrows(
                UnsupportedOperationException.class,
                () -> student.updateProfile("Changed", "changed@hsts.local")
        );

        assertEquals("User profile data is externally managed", failure.getMessage());
        assertEquals("Student", student.getFullName());
        assertEquals("s@hsts.local", student.getEmail());
        assertEquals("student-secret-hash", student.getPasswordHash());
        for (User user : users) {
            assertEquals(
                    Object.class,
                    user.getClass().getMethod("toString").getDeclaringClass()
            );
            assertFalse(user.toString().contains(user.getPasswordHash()));
        }
    }

    private static void assertUser(User user, int userId, String fullName,
                                   String email, String passwordHash,
                                   UserStatus status, boolean active) {
        assertEquals(userId, user.getUserId());
        assertEquals(fullName, user.getFullName());
        assertEquals(email, user.getEmail());
        assertEquals(passwordHash, user.getPasswordHash());
        assertEquals(status, user.getStatus());
        assertEquals(active, user.isActive());
    }

    private static Question question() {
        return new Question(
                17,
                "Question",
                "Algebra",
                "MULTIPLE_CHOICE",
                "HARD",
                "ACTIVE",
                "",
                "One",
                "Two",
                "Three",
                "Four",
                1
        );
    }

    private static final class ContradictoryTeacher extends Teacher {
        private ContradictoryTeacher() {
            super(
                    601,
                    "Contradictory",
                    "contradictory@hsts.local",
                    "hash",
                    UserRole.COORDINATOR,
                    UserStatus.ACTIVE,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }
}
