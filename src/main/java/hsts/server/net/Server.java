package hsts.server.net;

import hsts.common.AddBotQuestionSourcesPayload;
import hsts.common.AddBotTextSourcePayload;
import hsts.common.AskCourseBotPayload;
import hsts.common.CourseBotIdPayload;
import hsts.common.CourseIdPayload;
import hsts.common.CreateExamPayload;
import hsts.common.CreateCourseBotPayload;
import hsts.common.CreateQuestionPayload;
import hsts.common.ExecutionCodePayload;
import hsts.common.ExecutionIdPayload;
import hsts.common.ExamVersionPayload;
import hsts.common.ExamVersionSelectionPayload;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.ExtendExecutionTimePayload;
import hsts.common.GenerateExamPayload;
import hsts.common.LoginRequestPayload;
import hsts.common.LoginResult;
import hsts.common.QuestionFilterPayload;
import hsts.common.QuestionIdPayload;
import hsts.common.QuestionVersionPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.RejectExamPayload;
import hsts.common.RemoveBotSourcePayload;
import hsts.common.ReportTargetPayload;
import hsts.common.ReportExportPayload;
import hsts.common.NotificationIdPayload;
import hsts.common.PublishSubmissionPayload;
import hsts.common.Response;
import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import hsts.common.ReviewSubmissionPayload;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.StartExamPayload;
import hsts.common.SubmissionIdPayload;
import hsts.common.UpdateExamPayload;
import hsts.common.UpdateCourseBotPayload;
import hsts.common.UpdateQuestionPayload;
import hsts.common.UploadBotSourcePayload;
import hsts.ocsf.AbstractServer;
import hsts.ocsf.ConnectionToClient;
import hsts.server.control.AuthService;
import hsts.server.control.CourseBotService;
import hsts.server.control.ExamExecutionService;
import hsts.server.control.ExamManagementService;
import hsts.server.control.GradingService;
import hsts.server.control.NotificationService;
import hsts.server.control.PrincipalOversightService;
import hsts.server.control.ReportService;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server extends AbstractServer {
    private static final String AUTHENTICATED_USER_ID = "hsts.auth.userId";
    private static final String AUTHENTICATED_SESSION_ID = "hsts.auth.sessionId";
    private static final long AUTO_SUBMISSION_PERIOD_SECONDS = 1L;

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
    private PrincipalOversightService principalOversightService;

    // COMPATIBILITY-ONLY: Runs internal automatic submission while the server is active.
    private ScheduledExecutorService autoSubmissionScheduler;

    public Server(int port, ExamManagementService examManagementService, AuthService authService) {
        this(port, examManagementService, authService, null, null);
    }

    public Server(int port, ExamManagementService examManagementService,
                  AuthService authService,
                  ExamExecutionService examExecutionService) {
        this(port, examManagementService, authService, examExecutionService, null);
    }

    public Server(int port, ExamManagementService examManagementService,
                  AuthService authService,
                  ExamExecutionService examExecutionService,
                  ReportService reportService) {
        this(port, examManagementService, authService, examExecutionService,
                reportService, null);
    }

    public Server(int port, ExamManagementService examManagementService,
                  AuthService authService,
                  ExamExecutionService examExecutionService,
                  ReportService reportService,
                  CourseBotService courseBotService) {
        this(port, examManagementService, authService, examExecutionService,
                reportService, courseBotService, null);
    }

    public Server(int port, ExamManagementService examManagementService,
                  AuthService authService,
                  ExamExecutionService examExecutionService,
                  ReportService reportService,
                  CourseBotService courseBotService,
                  PrincipalOversightService principalOversightService) {
        this(port, examManagementService, authService, examExecutionService,
                reportService, courseBotService, principalOversightService, null);
    }

    public Server(int port, ExamManagementService examManagementService,
                  AuthService authService,
                  ExamExecutionService examExecutionService,
                  ReportService reportService,
                  CourseBotService courseBotService,
                  PrincipalOversightService principalOversightService,
                  NotificationService notificationService) {
        super(port);
        this.port = port;
        this.examManagementService = examManagementService;
        this.authService = authService;
        this.examExecutionService = examExecutionService;
        this.reportService = reportService;
        this.courseBotService = courseBotService;
        this.principalOversightService = principalOversightService;
        this.notificationService = notificationService;
    }

    public void startServer() {
        try {
            listen();
        } catch (IOException e) {
            throw new IllegalStateException("Server failed", e);
        }
    }

    public void stopServer() {
        close();
    }

    public Response receiveRequest(Request request) {
        return handleRequest(request);
    }

    public Response handleRequest(Request request) {
        try {
            RequestType type = request.getType();

            return switch (type) {
                case GET_ALL_QUESTIONS -> Response.success(
                        "Questions loaded successfully",
                        examManagementService.getAllQuestions()
                );

                case GET_QUESTION_BY_ID -> {
                    int questionId = (Integer) request.getPayload();
                    yield Response.success(
                            "Question loaded successfully",
                            examManagementService.getQuestionById(questionId)
                    );
                }

                case LOGIN -> {
                    if (!(request.getPayload() instanceof LoginRequestPayload payload)) {
                        throw new IllegalArgumentException("Login request data is required");
                    }
                    yield Response.success(
                            "Login successful",
                            authService.login(payload)
                    );
                }

                case LOGOUT -> Response.error("Connection context required");

                case UPDATE_QUESTION, GET_MY_COURSES, LIST_QUESTIONS, CREATE_QUESTION,
                     ACTIVATE_QUESTION, DEACTIVATE_QUESTION, GET_QUESTION_HISTORY,
                     LIST_MY_EXAMS, GET_MY_EXAM, CREATE_EXAM, GENERATE_EXAM,
                     LIST_PENDING_EXAMS, GET_PENDING_EXAM, UPDATE_EXAM,
                     SUBMIT_EXAM_FOR_APPROVAL, APPROVE_EXAM, REJECT_EXAM,
                     SCHEDULE_EXAM_EXECUTION, LIST_MY_EXAM_EXECUTIONS,
                     VALIDATE_EXECUTION_CODE, START_EXAM_ATTEMPT,
                     GET_ACTIVE_EXAM_ATTEMPT, SAVE_EXAM_ANSWER,
                     SUBMIT_EXAM_ATTEMPT, EXTEND_SUBMISSION_TIME,
                     EXTEND_EXAM_EXECUTION,
                     LIST_EXECUTION_SUBMISSIONS, GET_SUBMISSION_FOR_REVIEW,
                     REVIEW_SUBMISSION_GRADE, PUBLISH_SUBMISSION_GRADE,
                     LIST_MY_PUBLISHED_GRADES, GET_MY_PUBLISHED_GRADE,
                     GET_MY_PUBLISHED_EXAM_REVIEW,
                     GET_MY_AUTHORED_EXAMS_REPORT, GET_TEACHER_EXAMS_REPORT,
                     GET_COURSE_EXAMS_REPORT, GET_STUDENT_EXAMS_REPORT,
                     GET_EXAM_EXECUTION_REPORT, EXPORT_REPORT,
                     LIST_MY_NOTIFICATIONS, GET_UNREAD_NOTIFICATION_COUNT,
                     MARK_NOTIFICATION_READ, LIST_MY_COURSE_BOTS,
                     CREATE_COURSE_BOT, UPDATE_COURSE_BOT, GET_BOT_SOURCES,
                     ADD_BOT_TEXT_SOURCE, UPLOAD_BOT_SOURCE,
                     ADD_BOT_QUESTION_SOURCES, REMOVE_BOT_SOURCE, GET_BOT_USAGE,
                     LIST_MY_AVAILABLE_BOTS, GET_MY_BOT_HISTORY, ASK_COURSE_BOT,
                     LIST_ALL_QUESTIONS, LIST_QUESTION_VERSIONS_FOR_PRINCIPAL,
                     GET_QUESTION_VERSION_FOR_PRINCIPAL, LIST_ALL_EXAMS,
                     LIST_EXAM_VERSIONS_FOR_PRINCIPAL,
                     GET_EXAM_VERSION_FOR_PRINCIPAL, LIST_ALL_EXECUTIONS,
                     LIST_EXECUTION_RESULTS_FOR_PRINCIPAL,
                     GET_SUBMISSION_RESULT_FOR_PRINCIPAL ->
                        Response.error("Authentication context required");
            };

        } catch (Exception e) {
            return Response.error(e.getMessage());
        }
    }

    Response handleAuthenticatedRequest(Request request, int authenticatedUserId) {
        try {
            return switch (request.getType()) {
                case GET_ALL_QUESTIONS -> Response.success(
                        "Questions loaded successfully",
                        examManagementService.getQuestions(authenticatedUserId, null)
                );

                case GET_QUESTION_BY_ID -> {
                    int questionId = (Integer) request.getPayload();
                    yield Response.success(
                            "Question loaded successfully",
                            examManagementService.getQuestionById(
                                    authenticatedUserId,
                                    questionId
                            )
                    );
                }

                case UPDATE_QUESTION -> {
                    UpdateQuestionPayload payload = (UpdateQuestionPayload) request.getPayload();
                    yield Response.success(
                            "Question updated successfully",
                            examManagementService.updateQuestion(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case GET_MY_COURSES -> Response.success(
                        "Courses loaded successfully",
                        examManagementService.getCoursesForTeacher(authenticatedUserId)
                );

                case LIST_QUESTIONS -> {
                    Object requestPayload = request.getPayload();
                    if (requestPayload != null
                            && !(requestPayload instanceof QuestionFilterPayload)) {
                        throw new IllegalArgumentException("Question filter data is invalid");
                    }
                    yield Response.success(
                            "Questions loaded successfully",
                            examManagementService.getQuestions(
                                    authenticatedUserId,
                                    (QuestionFilterPayload) requestPayload
                            )
                    );
                }

                case CREATE_QUESTION -> {
                    if (!(request.getPayload() instanceof CreateQuestionPayload payload)) {
                        throw new IllegalArgumentException("Question data is required");
                    }
                    yield Response.success(
                            "Question created successfully",
                            examManagementService.createQuestion(authenticatedUserId, payload)
                    );
                }

                case ACTIVATE_QUESTION -> {
                    int questionId = requireQuestionIdPayload(request).getQuestionId();
                    yield Response.success(
                            "Question activated successfully",
                            examManagementService.activateQuestion(
                                    authenticatedUserId,
                                    questionId
                            )
                    );
                }

                case DEACTIVATE_QUESTION -> {
                    int questionId = requireQuestionIdPayload(request).getQuestionId();
                    yield Response.success(
                            "Question deactivated successfully",
                            examManagementService.deactivateQuestion(
                                    authenticatedUserId,
                                    questionId
                            )
                    );
                }

                case GET_QUESTION_HISTORY -> {
                    int questionId = requireQuestionIdPayload(request).getQuestionId();
                    yield Response.success(
                            "Question history loaded successfully",
                            examManagementService.getQuestionHistory(
                                    authenticatedUserId,
                                    questionId
                            )
                    );
                }

                case LIST_MY_EXAMS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "Exams loaded successfully",
                            examManagementService.getMyExams(authenticatedUserId)
                    );
                }

                case GET_MY_EXAM -> {
                    int examId = requireExamIdPayload(request);
                    yield Response.success(
                            "Exam loaded successfully",
                            examManagementService.getExamForTeacher(
                                    authenticatedUserId,
                                    examId
                            )
                    );
                }

                case CREATE_EXAM -> {
                    if (!(request.getPayload() instanceof CreateExamPayload payload)) {
                        throw new IllegalArgumentException("Exam creation data is missing");
                    }
                    yield Response.success(
                            "Exam created successfully",
                            examManagementService.createExam(authenticatedUserId, payload)
                    );
                }

                case GENERATE_EXAM -> {
                    if (!(request.getPayload() instanceof GenerateExamPayload payload)) {
                        throw new IllegalArgumentException("Automatic exam data is missing");
                    }
                    yield Response.success(
                            "Exam generated successfully",
                            examManagementService.generateAutomaticExam(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case LIST_PENDING_EXAMS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "Pending exams loaded successfully",
                            examManagementService.getPendingExams(authenticatedUserId)
                    );
                }

                case GET_PENDING_EXAM -> {
                    int examId = requireExamIdPayload(request);
                    yield Response.success(
                            "Pending exam loaded successfully",
                            examManagementService.getExamForCoordinator(
                                    authenticatedUserId,
                                    examId
                            )
                    );
                }

                case UPDATE_EXAM -> {
                    if (!(request.getPayload() instanceof UpdateExamPayload payload)) {
                        throw new IllegalArgumentException("Exam update data is missing");
                    }
                    yield Response.success(
                            "Exam updated successfully",
                            examManagementService.updateExam(authenticatedUserId, payload)
                    );
                }

                case SUBMIT_EXAM_FOR_APPROVAL -> {
                    if (!(request.getPayload() instanceof ExamVersionPayload payload)) {
                        throw new IllegalArgumentException("Exam version data is missing");
                    }
                    yield Response.success(
                            "Exam submitted for approval",
                            examManagementService.submitExamForApproval(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case APPROVE_EXAM -> {
                    if (!(request.getPayload() instanceof ExamVersionPayload payload)) {
                        throw new IllegalArgumentException("Exam version data is missing");
                    }
                    Object approvedExam =
                            examManagementService.approveExam(authenticatedUserId, payload);
                    publishEvent(new ServerEvent(
                            ServerEventType.EXAM_APPROVAL_CHANGED, 0
                    ));
                    yield Response.success("Exam approved successfully", approvedExam);
                }

                case REJECT_EXAM -> {
                    if (!(request.getPayload() instanceof RejectExamPayload payload)) {
                        throw new IllegalArgumentException("Exam rejection data is missing");
                    }
                    Object rejectedExam =
                            examManagementService.rejectExam(authenticatedUserId, payload);
                    publishEvent(new ServerEvent(
                            ServerEventType.EXAM_APPROVAL_CHANGED, 0
                    ));
                    yield Response.success("Exam rejected successfully", rejectedExam);
                }

                case SCHEDULE_EXAM_EXECUTION -> {
                    if (!(request.getPayload()
                            instanceof ScheduleExamExecutionPayload payload)) {
                        throw new IllegalArgumentException(
                                "Execution scheduling data is missing"
                        );
                    }
                    yield Response.success(
                            "Exam execution scheduled successfully",
                            requireExamExecutionService().scheduleExecution(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case LIST_MY_EXAM_EXECUTIONS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "Exam executions loaded successfully",
                            requireExamExecutionService().getMyExecutions(
                                    authenticatedUserId
                            )
                    );
                }

                case VALIDATE_EXECUTION_CODE -> {
                    if (!(request.getPayload() instanceof ExecutionCodePayload payload)) {
                        throw new IllegalArgumentException("Execution code is required");
                    }
                    yield Response.success(
                            "Execution code validated successfully",
                            requireExamExecutionService().validateExecutionCode(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case START_EXAM_ATTEMPT -> {
                    if (!(request.getPayload() instanceof StartExamPayload payload)) {
                        throw new IllegalArgumentException("Exam attempt data is missing");
                    }
                    yield Response.success(
                            "Exam attempt started successfully",
                            requireExamExecutionService().startOrResumeExam(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case GET_ACTIVE_EXAM_ATTEMPT -> {
                    if (!(request.getPayload() instanceof SubmissionIdPayload payload)) {
                        throw new IllegalArgumentException("Submission data is missing");
                    }
                    yield Response.success(
                            "Exam attempt loaded successfully",
                            requireExamExecutionService().getActiveAttempt(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case SAVE_EXAM_ANSWER -> {
                    if (!(request.getPayload() instanceof SaveExamAnswerPayload payload)) {
                        throw new IllegalArgumentException("Answer data is missing");
                    }
                    yield Response.success(
                            "Answer saved successfully",
                            requireExamExecutionService().saveAnswer(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case SUBMIT_EXAM_ATTEMPT -> {
                    if (!(request.getPayload() instanceof SubmissionIdPayload payload)) {
                        throw new IllegalArgumentException("Submission data is missing");
                    }
                    yield Response.success(
                            "Exam submitted successfully",
                            requireExamExecutionService().submitExam(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case EXTEND_SUBMISSION_TIME -> {
                    if (!(request.getPayload()
                            instanceof ExtendSubmissionTimePayload payload)) {
                        throw new IllegalArgumentException("Time extension data is missing");
                    }
                    Object extendedSubmission = requireExamExecutionService()
                            .extendStudentTime(authenticatedUserId, payload);
                    publishEvent(new ServerEvent(ServerEventType.EXAM_TIME_EXTENDED, 0));
                    yield Response.success(
                            "Exam time extended successfully",
                            extendedSubmission
                    );
                }

                case EXTEND_EXAM_EXECUTION -> {
                    if (!(request.getPayload()
                            instanceof ExtendExecutionTimePayload payload)) {
                        throw new IllegalArgumentException(
                                "Time extension data is missing"
                        );
                    }
                    Object extendedExecution = requireExamExecutionService()
                            .extendExecutionTime(authenticatedUserId, payload);
                    publishEvent(new ServerEvent(
                            ServerEventType.EXAM_TIME_EXTENDED,
                            payload.getExecutionId()
                    ));
                    yield Response.success(
                            "Exam execution extended successfully",
                            extendedExecution
                    );
                }

                case LIST_EXECUTION_SUBMISSIONS -> {
                    if (!(request.getPayload() instanceof ExecutionIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Execution request data is invalid"
                        );
                    }
                    yield Response.success(
                            "Execution submissions loaded successfully",
                            requireExamExecutionService().getExecutionSubmissions(
                                    authenticatedUserId,
                                    payload.getExecutionId()
                            )
                    );
                }

                case GET_SUBMISSION_FOR_REVIEW -> {
                    if (!(request.getPayload() instanceof SubmissionIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Submission request data is invalid"
                        );
                    }
                    yield Response.success(
                            "Submission loaded successfully",
                            requireExamExecutionService().getSubmissionForReview(
                                    authenticatedUserId,
                                    payload.getSubmissionId()
                            )
                    );
                }

                case REVIEW_SUBMISSION_GRADE -> {
                    if (!(request.getPayload() instanceof ReviewSubmissionPayload payload)) {
                        throw new IllegalArgumentException("Grade review data is invalid");
                    }
                    yield Response.success(
                            "Submission grade reviewed successfully",
                            requireExamExecutionService().reviewSubmissionGrade(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case PUBLISH_SUBMISSION_GRADE -> {
                    if (!(request.getPayload() instanceof PublishSubmissionPayload payload)) {
                        throw new IllegalArgumentException(
                                "Grade publication data is invalid"
                        );
                    }
                    Object publishedGrade = requireExamExecutionService()
                            .publishSubmissionGrade(authenticatedUserId, payload);
                    publishEvent(new ServerEvent(ServerEventType.GRADES_PUBLISHED, 0));
                    yield Response.success(
                            "Submission grade published successfully",
                            publishedGrade
                    );
                }

                case LIST_MY_PUBLISHED_GRADES -> {
                    if (request.getPayload() != null) {
                        throw new IllegalArgumentException(
                                "Published grades request data is invalid"
                        );
                    }
                    yield Response.success(
                            "Published grades loaded successfully",
                            requireExamExecutionService().getMyPublishedGrades(
                                    authenticatedUserId
                            )
                    );
                }

                case GET_MY_PUBLISHED_GRADE -> {
                    if (!(request.getPayload() instanceof SubmissionIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Submission request data is invalid"
                        );
                    }
                    yield Response.success(
                            "Published grade loaded successfully",
                            requireExamExecutionService().getMyPublishedGrade(
                                    authenticatedUserId,
                                    payload.getSubmissionId()
                            )
                    );
                }

                case GET_MY_PUBLISHED_EXAM_REVIEW -> {
                    if (!(request.getPayload() instanceof SubmissionIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Submission payload is required"
                        );
                    }
                    yield Response.success(
                            "Published exam review loaded successfully",
                            requireExamExecutionService().getMyPublishedExamReview(
                                    authenticatedUserId,
                                    payload.getSubmissionId()
                            )
                    );
                }

                case GET_MY_AUTHORED_EXAMS_REPORT -> {
                    if (request.getPayload() != null) {
                        throw new IllegalArgumentException(
                                "Authored exam report payload must be empty"
                        );
                    }
                    yield Response.success(
                            "Authored exam report loaded",
                            requireReportService().getMyAuthoredExamsReport(
                                    authenticatedUserId
                            )
                    );
                }

                case GET_TEACHER_EXAMS_REPORT -> {
                    ReportTargetPayload payload = requireReportTargetPayload(request);
                    yield Response.success(
                            "Teacher exam report loaded",
                            requireReportService().getTeacherExamsReport(
                                    authenticatedUserId,
                                    payload.getTargetId()
                            )
                    );
                }

                case GET_COURSE_EXAMS_REPORT -> {
                    ReportTargetPayload payload = requireReportTargetPayload(request);
                    yield Response.success(
                            "Course exam report loaded",
                            requireReportService().getCourseExamsReport(
                                    authenticatedUserId,
                                    payload.getTargetId()
                            )
                    );
                }

                case GET_STUDENT_EXAMS_REPORT -> {
                    ReportTargetPayload payload = requireReportTargetPayload(request);
                    yield Response.success(
                            "Student exam report loaded",
                            requireReportService().getStudentExamsReport(
                                    authenticatedUserId,
                                    payload.getTargetId()
                            )
                    );
                }

                case GET_EXAM_EXECUTION_REPORT -> {
                    ReportTargetPayload payload = requireReportTargetPayload(request);
                    yield Response.success(
                            "Exam execution report loaded",
                            requireReportService().getExamExecutionReport(
                                    authenticatedUserId,
                                    payload.getTargetId()
                            )
                    );
                }

                case EXPORT_REPORT -> {
                    if (!(request.getPayload() instanceof ReportExportPayload payload)) {
                        throw new IllegalArgumentException(
                                "Report export data is required"
                        );
                    }
                    yield Response.success(
                            "Report exported successfully",
                            requireReportService().exportReport(
                                    authenticatedUserId,
                                    payload
                            )
                    );
                }

                case LIST_MY_NOTIFICATIONS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "Notifications loaded successfully",
                            requireNotificationService().getMyNotifications(
                                    authenticatedUserId
                            )
                    );
                }

                case GET_UNREAD_NOTIFICATION_COUNT -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "Unread notification count loaded successfully",
                            requireNotificationService().getUnreadCount(
                                    authenticatedUserId
                            )
                    );
                }

                case MARK_NOTIFICATION_READ -> {
                    if (!(request.getPayload() instanceof NotificationIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Notification data is required"
                        );
                    }
                    yield Response.success(
                            "Notification marked as read",
                            requireNotificationService().markAsRead(
                                    authenticatedUserId,
                                    payload.getNotificationId()
                            )
                    );
                }

                case LIST_MY_COURSE_BOTS -> {
                    if (request.getPayload() != null) {
                        throw new IllegalArgumentException(
                                "Course Bot list payload must be empty"
                        );
                    }
                    yield Response.success(
                            "Course Bots loaded",
                            requireCourseBotService().getMyCourseBots(authenticatedUserId)
                    );
                }

                case CREATE_COURSE_BOT -> {
                    if (!(request.getPayload() instanceof CreateCourseBotPayload payload)) {
                        throw new IllegalArgumentException(
                                "Create Course Bot payload is required"
                        );
                    }
                    yield Response.success(
                            "Course Bot created",
                            requireCourseBotService().createCourseBot(
                                    authenticatedUserId, payload
                            )
                    );
                }

                case UPDATE_COURSE_BOT -> {
                    if (!(request.getPayload() instanceof UpdateCourseBotPayload payload)) {
                        throw new IllegalArgumentException(
                                "Update Course Bot payload is required"
                        );
                    }
                    yield Response.success(
                            "Course Bot updated",
                            requireCourseBotService().updateCourseBot(
                                    authenticatedUserId, payload
                            )
                    );
                }

                case GET_BOT_SOURCES -> {
                    if (!(request.getPayload() instanceof CourseBotIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Course Bot ID payload is required"
                        );
                    }
                    yield Response.success(
                            "Bot sources loaded",
                            requireCourseBotService().getBotSources(
                                    authenticatedUserId, payload.getBotId()
                            )
                    );
                }

                case ADD_BOT_TEXT_SOURCE -> {
                    if (!(request.getPayload() instanceof AddBotTextSourcePayload payload)) {
                        throw new IllegalArgumentException(
                                "Bot text source payload is required"
                        );
                    }
                    yield Response.success(
                            "Bot text source added",
                            requireCourseBotService().addTextSource(
                                    authenticatedUserId, payload
                            )
                    );
                }

                case UPLOAD_BOT_SOURCE -> {
                    if (!(request.getPayload() instanceof UploadBotSourcePayload payload)) {
                        throw new IllegalArgumentException(
                                "Bot file source payload is required"
                        );
                    }
                    yield Response.success(
                            "Bot file source added",
                            requireCourseBotService().uploadSource(
                                    authenticatedUserId, payload
                            )
                    );
                }

                case ADD_BOT_QUESTION_SOURCES -> {
                    if (!(request.getPayload()
                            instanceof AddBotQuestionSourcesPayload payload)) {
                        throw new IllegalArgumentException(
                                "Bot question sources payload is required"
                        );
                    }
                    yield Response.success(
                            "Bot question sources added",
                            requireCourseBotService().addQuestionSources(
                                    authenticatedUserId, payload
                            )
                    );
                }

                case REMOVE_BOT_SOURCE -> {
                    if (!(request.getPayload() instanceof RemoveBotSourcePayload payload)) {
                        throw new IllegalArgumentException(
                                "Remove Bot source payload is required"
                        );
                    }
                    yield Response.success(
                            "Bot source removed",
                            requireCourseBotService().removeSource(
                                    authenticatedUserId, payload
                            )
                    );
                }

                case GET_BOT_USAGE -> {
                    if (!(request.getPayload() instanceof CourseBotIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Course Bot ID payload is required"
                        );
                    }
                    yield Response.success(
                            "Bot usage loaded",
                            requireCourseBotService().getBotUsage(
                                    authenticatedUserId, payload.getBotId()
                            )
                    );
                }

                case LIST_MY_AVAILABLE_BOTS -> {
                    if (request.getPayload() != null) {
                        throw new IllegalArgumentException(
                                "Available Bot list payload must be empty"
                        );
                    }
                    yield Response.success(
                            "Available Course Bots loaded",
                            requireCourseBotService().getMyAvailableBots(
                                    authenticatedUserId
                            )
                    );
                }

                case GET_MY_BOT_HISTORY -> {
                    if (!(request.getPayload() instanceof CourseIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Course ID payload is required"
                        );
                    }
                    yield Response.success(
                            "Bot history loaded",
                            requireCourseBotService().getMyBotHistory(
                                    authenticatedUserId, payload.getCourseId()
                            )
                    );
                }

                case ASK_COURSE_BOT -> {
                    if (!(request.getPayload() instanceof AskCourseBotPayload payload)) {
                        throw new IllegalArgumentException(
                                "Ask Course Bot payload is required"
                        );
                    }
                    yield Response.success(
                            "Course Bot answer received",
                            requireCourseBotService().askCourseBot(
                                    authenticatedUserId, payload
                            )
                    );
                }

                case LIST_ALL_QUESTIONS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "All questions loaded successfully",
                            requirePrincipalOversightService().getAllQuestions(
                                    authenticatedUserId
                            )
                    );
                }

                case LIST_QUESTION_VERSIONS_FOR_PRINCIPAL -> {
                    QuestionIdPayload payload = requireQuestionIdPayload(request);
                    yield Response.success(
                            "Question versions loaded successfully",
                            requirePrincipalOversightService().getQuestionVersions(
                                    authenticatedUserId, payload.getQuestionId()
                            )
                    );
                }

                case GET_QUESTION_VERSION_FOR_PRINCIPAL -> {
                    if (!(request.getPayload() instanceof QuestionVersionPayload payload)) {
                        throw new IllegalArgumentException(
                                "Question version payload is required"
                        );
                    }
                    yield Response.success(
                            "Question version loaded successfully",
                            requirePrincipalOversightService().getQuestionVersion(
                                    authenticatedUserId, payload.getQuestionId(),
                                    payload.getVersionNo()
                            )
                    );
                }

                case LIST_ALL_EXAMS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "All exams loaded successfully",
                            requirePrincipalOversightService().getAllExams(
                                    authenticatedUserId
                            )
                    );
                }

                case LIST_EXAM_VERSIONS_FOR_PRINCIPAL -> {
                    int examId = requireExamIdPayload(request);
                    yield Response.success(
                            "Exam versions loaded successfully",
                            requirePrincipalOversightService().getExamVersions(
                                    authenticatedUserId, examId
                            )
                    );
                }

                case GET_EXAM_VERSION_FOR_PRINCIPAL -> {
                    if (!(request.getPayload()
                            instanceof ExamVersionSelectionPayload payload)) {
                        throw new IllegalArgumentException(
                                "Exam version payload is required"
                        );
                    }
                    yield Response.success(
                            "Exam version loaded successfully",
                            requirePrincipalOversightService().getExamVersion(
                                    authenticatedUserId, payload.getExamId(),
                                    payload.getVersionNo()
                            )
                    );
                }

                case LIST_ALL_EXECUTIONS -> {
                    requireEmptyPayload(request);
                    yield Response.success(
                            "All exam executions loaded successfully",
                            requirePrincipalOversightService().getAllExecutions(
                                    authenticatedUserId
                            )
                    );
                }

                case LIST_EXECUTION_RESULTS_FOR_PRINCIPAL -> {
                    if (!(request.getPayload() instanceof ExecutionIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Execution ID payload is required"
                        );
                    }
                    yield Response.success(
                            "Execution results loaded successfully",
                            requirePrincipalOversightService().getExecutionResults(
                                    authenticatedUserId, payload.getExecutionId()
                            )
                    );
                }

                case GET_SUBMISSION_RESULT_FOR_PRINCIPAL -> {
                    if (!(request.getPayload() instanceof SubmissionIdPayload payload)) {
                        throw new IllegalArgumentException(
                                "Submission ID payload is required"
                        );
                    }
                    yield Response.success(
                            "Submission result loaded successfully",
                            requirePrincipalOversightService().getSubmissionResult(
                                    authenticatedUserId, payload.getSubmissionId()
                            )
                    );
                }

                default -> handleRequest(request);
            };
        } catch (Exception exception) {
            return errorResponse(exception);
        }
    }

    public void sendResponse(Response response) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    // COMPATIBILITY-ONLY: OCSF supplies the client connection with each received message.
    @Override
    protected void handleMessageFromClient(Object message, ConnectionToClient client) {
        Response response;

        if (!(message instanceof Request request)) {
            response = Response.error("Unsupported message type");
            sendResponse(client, response);
            return;
        }

        if (request.getType() == RequestType.LOGIN) {
            if (isAuthenticated(client)) {
                response = Response.error("User is already logged in");
            } else {
                response = handleRequest(request);
                if (response.isSuccess() && response.getPayload() instanceof LoginResult loginResult) {
                    bindAuthentication(client, loginResult);
                }
            }
        } else if (!isAuthenticated(client)) {
            response = Response.error("Authentication required");
        } else if (request.getType() == RequestType.LOGOUT) {
            response = logout(client);
        } else {
            int authenticatedUserId = (Integer) client.getInfo(AUTHENTICATED_USER_ID);
            response = handleAuthenticatedRequest(request, authenticatedUserId);
        }
        sendResponse(client, response);
    }

    private void bindAuthentication(ConnectionToClient client, LoginResult loginResult) {
        client.setInfo(AUTHENTICATED_USER_ID, loginResult.getUserId());
        client.setInfo(AUTHENTICATED_SESSION_ID, loginResult.getSessionId());
    }

    private QuestionIdPayload requireQuestionIdPayload(Request request) {
        if (!(request.getPayload() instanceof QuestionIdPayload payload)) {
            throw new IllegalArgumentException("Question ID is required");
        }
        return payload;
    }

    private int requireExamIdPayload(Request request) {
        if (!(request.getPayload() instanceof Integer examId)) {
            throw new IllegalArgumentException("Exam ID is required");
        }
        return examId;
    }

    private void requireEmptyPayload(Request request) {
        if (request.getPayload() != null) {
            throw new IllegalArgumentException("Request payload must be empty");
        }
    }

    private ExamExecutionService requireExamExecutionService() {
        if (examExecutionService == null) {
            throw new IllegalStateException(
                    "Exam execution service is not configured"
            );
        }
        return examExecutionService;
    }

    private ReportTargetPayload requireReportTargetPayload(Request request) {
        if (!(request.getPayload() instanceof ReportTargetPayload payload)) {
            throw new IllegalArgumentException("Report target payload is required");
        }
        return payload;
    }

    private ReportService requireReportService() {
        if (reportService == null) {
            throw new IllegalStateException("Report service is not configured");
        }
        return reportService;
    }

    private NotificationService requireNotificationService() {
        if (notificationService == null) {
            throw new IllegalStateException("Notification service is not configured");
        }
        return notificationService;
    }

    private CourseBotService requireCourseBotService() {
        if (courseBotService == null) {
            throw new IllegalStateException("Course Bot service is unavailable");
        }
        return courseBotService;
    }

    private PrincipalOversightService requirePrincipalOversightService() {
        if (principalOversightService == null) {
            throw new IllegalStateException("Principal oversight service is unavailable");
        }
        return principalOversightService;
    }

    private Response errorResponse(Exception exception) {
        String message = exception.getMessage();
        return Response.error(
                message == null || message.isBlank() ? "Request failed" : message
        );
    }

    private boolean isAuthenticated(ConnectionToClient client) {
        Object userId = client.getInfo(AUTHENTICATED_USER_ID);
        Object sessionId = client.getInfo(AUTHENTICATED_SESSION_ID);

        if (userId instanceof Integer authenticatedUserId
                && sessionId instanceof String authenticatedSessionId) {
            if (authService.isSessionActive(authenticatedUserId, authenticatedSessionId)) {
                return true;
            }

            clearAuthentication(client);
        }

        return false;
    }

    private Response logout(ConnectionToClient client) {
        int userId = (Integer) client.getInfo(AUTHENTICATED_USER_ID);
        String sessionId = (String) client.getInfo(AUTHENTICATED_SESSION_ID);

        try {
            authService.logout(userId, sessionId);
            return Response.success("Logout successful", null);
        } catch (Exception exception) {
            return Response.error(exception.getMessage());
        } finally {
            clearAuthentication(client);
        }
    }

    private void clearAuthentication(ConnectionToClient client) {
        client.setInfo(AUTHENTICATED_USER_ID, null);
        client.setInfo(AUTHENTICATED_SESSION_ID, null);
    }

    private void cleanupAuthentication(ConnectionToClient client) {
        Object userId = client.getInfo(AUTHENTICATED_USER_ID);
        Object sessionId = client.getInfo(AUTHENTICATED_SESSION_ID);

        try {
            if (userId instanceof Integer authenticatedUserId
                    && sessionId instanceof String authenticatedSessionId) {
                authService.logout(authenticatedUserId, authenticatedSessionId);
            }
        } catch (Exception exception) {
            System.out.println("Failed to clean up client authentication");
        } finally {
            clearAuthentication(client);
        }
    }

    // COMPATIBILITY-ONLY: OCSF response delivery requires a target client connection.
    /**
     * Pushes a state-change event to every authenticated client.
     *
     * <p>Screens subscribe to these events and refresh themselves, so the user
     * never has to initiate a screen refresh. Delivery is best effort: a
     * failure to reach one client must not affect the request being served.</p>
     */
    void publishEvent(ServerEvent event) {
        if (event == null) {
            return;
        }

        for (ConnectionToClient client : getClientConnections()) {
            if (!(client.getInfo(AUTHENTICATED_USER_ID) instanceof Integer)) {
                continue;
            }

            try {
                client.sendToClient(event);
            } catch (IOException | RuntimeException exception) {
                System.out.println(
                        "Failed to push " + event.getType() + " to a client: "
                                + exception.getMessage()
                );
            }
        }
    }

    private void sendResponse(ConnectionToClient client, Response response) {
        try {
            client.sendToClient(response);
        } catch (IOException e) {
            System.out.println("Failed to send response to client: " + e.getMessage());
        }
    }

    @Override
    protected void serverStarted() {
        running = true;
        startAutoSubmissionScheduler();
        System.out.println("HSTS OCSF server started on port " + getPort());
    }

    @Override
    protected void serverStopped() {
        running = false;
        stopAutoSubmissionScheduler();
    }

    @Override
    protected void clientConnected(ConnectionToClient client) {
        System.out.println("Client connected: " + client);
    }

    @Override
    protected void clientDisconnected(ConnectionToClient client) {
        cleanupAuthentication(client);
        System.out.println("Client disconnected: " + client);
    }

    @Override
    protected void listeningException(Exception exception) {
        System.out.println("Server listening error: " + exception.getMessage());
    }

    @Override
    protected void serverClosed() {
        running = false;
        stopAutoSubmissionScheduler();
        System.out.println("HSTS OCSF server closed");
    }

    void runAutoSubmissionCycle() {
        if (examExecutionService == null) {
            return;
        }
        try {
            examExecutionService.autoSubmitExpired();
        } catch (RuntimeException exception) {
            System.out.println("Automatic exam submission cycle failed");
        }
    }

    private synchronized void startAutoSubmissionScheduler() {
        if (examExecutionService == null
                || (autoSubmissionScheduler != null
                && !autoSubmissionScheduler.isShutdown())) {
            return;
        }

        autoSubmissionScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hsts-auto-exam-submission");
            thread.setDaemon(true);
            return thread;
        });
        autoSubmissionScheduler.scheduleAtFixedRate(
                this::runAutoSubmissionCycle,
                AUTO_SUBMISSION_PERIOD_SECONDS,
                AUTO_SUBMISSION_PERIOD_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private synchronized void stopAutoSubmissionScheduler() {
        if (autoSubmissionScheduler == null) {
            return;
        }
        autoSubmissionScheduler.shutdownNow();
        autoSubmissionScheduler = null;
    }
}
