package hsts.server.entity;

import java.util.List;

public class Student extends User {
    private int studentId;
    private String className;
    private String gradeLevel;

    // RELATIONSHIP-DERIVED: Student participates in the many-to-many Course-Student association.
    private List<Course> courses;

    // RELATIONSHIP-DERIVED: Student submits zero or more exam submissions.
    private List<ExamSubmission> examSubmissions;

    public List getCourses() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
