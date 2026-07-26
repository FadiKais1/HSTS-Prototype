package hsts.server.entity;

import java.util.List;

public class Principal extends User {
    private int principalId;
    private String schoolName;

    // RELATIONSHIP-DERIVED: Principal receives zero or more reports.
    private List<Report> reports;
}
