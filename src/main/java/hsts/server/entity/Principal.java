package hsts.server.entity;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;

import java.util.List;

public class Principal extends User {
    private int principalId;
    // EXTERNAL-DATA: Not present in the users schema and not loaded by UserRepository.
    private String schoolName;

    // RELATIONSHIP-DERIVED: Principal receives zero or more reports.
    private List<Report> reports;

    private Principal(int userId, String fullName, String email, String passwordHash,
                      UserStatus status, List<Notification> notifications,
                      List<Report> reports) {
        super(userId, fullName, email, passwordHash, UserRole.PRINCIPAL, status,
                notifications);
        // COMPATIBILITY-ONLY: The diagram's principalId is the users-table account identity.
        this.principalId = userId;
        this.reports = immutableRelationshipCopy(reports, "Principal reports are required");
    }

    public static Principal rehydrate(int userId, String fullName, String email,
                                      String passwordHash, UserStatus status) {
        return rehydrate(
                userId, fullName, email, passwordHash, status,
                List.of(), List.of()
        );
    }

    static Principal rehydrate(int userId, String fullName, String email,
                               String passwordHash, UserStatus status,
                               List<Notification> notifications,
                               List<Report> reports) {
        return new Principal(
                userId, fullName, email, passwordHash, status,
                notifications, reports
        );
    }

    public int getPrincipalId() {
        return principalId;
    }

    public List<Report> getReports() {
        return reports;
    }
}
