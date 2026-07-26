package hsts.server.net;

import hsts.common.Request;
import hsts.common.Response;
import hsts.server.control.AuthService;
import hsts.server.control.CourseBotService;
import hsts.server.control.ExamExecutionService;
import hsts.server.control.ExamManagementService;
import hsts.server.control.GradingService;
import hsts.server.control.NotificationService;
import hsts.server.control.ReportService;

public class Server {
    private int port;
    private boolean running;

    // RELATIONSHIP-DERIVED: Server dispatches requests to the Control layer.
    private ExamManagementService examManagementService;
    private ExamExecutionService examExecutionService;
    private AuthService authService;
    private GradingService gradingService;
    private ReportService reportService;
    private NotificationService notificationService;
    private CourseBotService courseBotService;

    public void startServer() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void stopServer() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public Response receiveRequest(Request request) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public Response handleRequest(Request request) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void sendResponse(Response response) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
