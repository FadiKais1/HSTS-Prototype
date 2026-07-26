package hsts.server.entity;

import java.util.List;

public class Coordinator extends Teacher {
    private int coordinatorId;
    private String departmentName;

    // RELATIONSHIP-DERIVED: Coordinator approves or rejects zero or more exams.
    private List<Exam> reviewedExams;
}
