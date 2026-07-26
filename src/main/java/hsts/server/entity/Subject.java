package hsts.server.entity;

import java.util.List;

public class Subject {
    private int subjectId;
    private String subjectCode;
    private String name;
    private String description;

    // RELATIONSHIP-DERIVED: Subject contains zero or more courses.
    private List<Course> courses;

    public List getCourses() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
