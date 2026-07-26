package hsts.server.entity;

import java.util.List;

public class Teacher extends User {
    private int teacherId;
    private String specialization;

    // RELATIONSHIP-DERIVED: Teacher creates or updates zero or more questions.
    private List<Question> questions;

    // RELATIONSHIP-DERIVED: Teacher creates zero or more exams.
    private List<Exam> exams;

    public List getCourses() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
