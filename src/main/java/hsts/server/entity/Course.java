package hsts.server.entity;

import java.util.List;

public class Course {
    private int courseId;
    private String courseCode;
    private String name;
    private String gradeLevel;
    private String schoolYear;

    // RELATIONSHIP-DERIVED: Course is associated with zero or more questions.
    private List<Question> questions;

    // RELATIONSHIP-DERIVED: Course is associated with zero or more exams.
    private List<Exam> exams;

    // RELATIONSHIP-DERIVED: Course participates in the many-to-many Course-Student association.
    private List<Student> students;

    public List getActiveQuestions() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getExams() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
