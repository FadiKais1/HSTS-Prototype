package hsts.server.control;

import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PublishedGradeDTO;
import hsts.common.PublishedGradeSummaryDTO;
import hsts.common.PublishedExamReviewDTO;
import hsts.common.StudentAnswerDTO;
import hsts.common.StudentExamQuestionDTO;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.common.type.SubmissionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Coordinator;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamExecution;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.Principal;
import hsts.server.entity.Question;
import hsts.server.entity.Student;
import hsts.server.entity.StudentAnswer;
import hsts.server.entity.Teacher;
import hsts.server.entity.User;
import hsts.server.repository.ExamExecutionRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.ExamSubmissionRepository;
import hsts.server.repository.StudentEnrollmentRepository;
import hsts.server.repository.StudentProfileRepository;
import hsts.server.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ExamExecutionServiceTestSupport {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 8, 0);
    static final Clock CLOCK = Clock.fixed(
            NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
    );

    private ExamExecutionServiceTestSupport() {
    }

    static ExamExecutionService service(RecordingExecutionRepository executions,
                                        RecordingSubmissionRepository submissions,
                                        RecordingEnrollmentRepository enrollments,
                                        RecordingProfileRepository profiles,
                                        RecordingUserRepository users) {
        return service(
                executions,
                submissions,
                enrollments,
                profiles,
                users,
                new RecordingExamRepository()
        );
    }

    static ExamExecutionService service(RecordingExecutionRepository executions,
                                        RecordingSubmissionRepository submissions,
                                        RecordingEnrollmentRepository enrollments,
                                        RecordingProfileRepository profiles,
                                        RecordingUserRepository users,
                                        RecordingExamRepository exams) {
        return service(
                executions,
                submissions,
                enrollments,
                profiles,
                users,
                exams,
                new RecordingGradingService()
        );
    }

    static ExamExecutionService service(RecordingExecutionRepository executions,
                                        RecordingSubmissionRepository submissions,
                                        RecordingEnrollmentRepository enrollments,
                                        RecordingProfileRepository profiles,
                                        RecordingUserRepository users,
                                        RecordingExamRepository exams,
                                        RecordingGradingService grading) {
        return new ExamExecutionService(
                executions,
                submissions,
                enrollments,
                profiles,
                users,
                exams,
                grading,
                CLOCK
        );
    }

    static User user(int id, UserRole role, UserStatus status) {
        String fullName = "Development User " + id;
        String email = "user" + id + "@hsts.local";
        return switch (role) {
            case STUDENT -> Student.rehydrate(id, fullName, email, "stored-hash", status);
            case TEACHER -> Teacher.rehydrate(id, fullName, email, "stored-hash", status);
            case COORDINATOR -> Coordinator.rehydrate(
                    id, fullName, email, "stored-hash", status
            );
            case PRINCIPAL -> Principal.rehydrate(id, fullName, email, "stored-hash", status);
        };
    }

    static ExamExecutionSummaryDTO summary(int executionId, int creatorId) {
        return new ExamExecutionSummaryDTO(
                executionId,
                "A1B2",
                40,
                3,
                "EX1234",
                "Approved Midterm",
                7,
                "Mathematics",
                NOW.plusHours(1),
                NOW.plusHours(3),
                75,
                ExecutionStatus.SCHEDULED,
                creatorId,
                "Development User " + creatorId,
                NOW.minusDays(1),
                0,
                0,
                0
        );
    }

    static ExamExecutionPreviewDTO preview(ExecutionStatus status,
                                           LocalDateTime opening,
                                           LocalDateTime closing,
                                           boolean resumable) {
        return new ExamExecutionPreviewDTO(
                81,
                "A1B2",
                40,
                3,
                "Approved Midterm",
                7,
                "Mathematics",
                opening,
                closing,
                75,
                status,
                resumable
        );
    }

    static ExamAttemptDTO attempt(SubmissionStatus status) {
        LocalDateTime started = NOW.minusMinutes(5);
        return new ExamAttemptDTO(
                501,
                81,
                "A1B2",
                40,
                3,
                "Approved Midterm",
                "Read carefully",
                started,
                started.plusMinutes(75),
                75,
                0,
                70 * 60L,
                status,
                List.of(new StudentExamQuestionDTO(
                        17, 4, 1, 100.0, "Historical question", "Algebra",
                        "HARD", "", "One", "Two", "Three", "Four"
                )),
                List.of(new StudentAnswerDTO(17, 2, NOW.minusMinutes(1)))
        );
    }

    static ExamExecution execution(ExecutionStatus status,
                                   LocalDateTime opening,
                                   LocalDateTime closing) {
        LocalDateTime created = opening.minusDays(1);
        LocalDateTime closed = status == ExecutionStatus.CLOSED ? closing : null;
        LocalDateTime updated = closed == null ? created : closed;
        return ExamExecution.rehydrate(
                81,
                "A1B2",
                40,
                3,
                opening,
                closing,
                75,
                status,
                1002,
                created,
                closed,
                null,
                null,
                List.of(),
                0,
                0,
                0,
                updated,
                List.of()
        );
    }

    static ExamSubmission submission(int studentUserId,
                                     List<StudentAnswer> answers) {
        return submission(501, studentUserId, NOW.minusMinutes(5), answers);
    }

    static ExamSubmission submission(int submissionId, int studentUserId,
                                     LocalDateTime started,
                                     List<StudentAnswer> answers) {
        return ExamSubmission.rehydrate(
                submissionId,
                81,
                40,
                3,
                studentUserId,
                started,
                null,
                SubmissionStatus.IN_PROGRESS,
                75,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                started,
                started,
                answers
        );
    }

    static StudentAnswer answer(int answerId, int submissionId,
                                int selectedOptionNumber,
                                LocalDateTime answeredAt) {
        return StudentAnswer.rehydrate(
                answerId,
                submissionId,
                17,
                4,
                selectedOptionNumber,
                null,
                null,
                null,
                answeredAt,
                answeredAt
        );
    }

    static Exam exactExam() {
        LocalDateTime created = NOW.minusDays(5);
        Question question = Question.rehydrate(
                17,
                "Historical question",
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.HARD,
                QuestionStatus.INACTIVE,
                created,
                created,
                "Algebra",
                "",
                List.of(
                        new AnswerOption(1, "One", false),
                        new AnswerOption(2, "Two", false),
                        new AnswerOption(3, "Three", true),
                        new AnswerOption(4, "Four", false)
                )
        );
        return Exam.rehydrate(
                40,
                "EX1234",
                7,
                1002,
                3,
                "Approved Midterm",
                75,
                "Teacher notes",
                "Read carefully",
                ExamStatus.APPROVED,
                created,
                NOW.minusDays(2),
                NOW.minusDays(3),
                1003,
                NOW.minusDays(2),
                null,
                List.of(new ExamQuestion(
                        17,
                        4,
                        1,
                        new BigDecimal("100.00"),
                        question
                ))
        );
    }

    static final class RecordingUserRepository extends UserRepository {
        private final Map<Integer, User> users = new LinkedHashMap<>();
        RuntimeException findFailure;
        int findCalls;
        int updateCalls;

        RecordingUserRepository(User... initialUsers) {
            for (User user : initialUsers) {
                users.put(user.getUserId(), user);
            }
        }

        @Override
        public Optional<User> findById(int userId) {
            findCalls++;
            if (findFailure != null) {
                throw findFailure;
            }
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public boolean updateStatus(int userId, UserStatus status) {
            updateCalls++;
            throw new AssertionError("Execution service must not update users");
        }
    }

    static final class RecordingExecutionRepository extends ExamExecutionRepository {
        int createdId = 81;
        int scheduleCalls;
        int listCalls;
        int detailCalls;
        int codeCalls;
        int entityCodeCalls;
        int entityStudentCalls;
        int lastManagerId;
        int lastExecutionId;
        int lastStudentId;
        String lastCode;
        int lastExamId;
        int lastExamVersionNo;
        LocalDateTime lastOpeningTime;
        LocalDateTime lastClosingTime;
        List<ExamExecutionSummaryDTO> summaries = new ArrayList<>();
        ExamExecutionSummaryDTO detail;
        ExamExecutionPreviewDTO preview;
        ExamExecution executionEntity;
        RuntimeException failure;

        @Override
        public ExamExecution schedule(int authenticatedUserId, int examId,
                                      int examVersionNo, LocalDateTime openingTime,
                                      LocalDateTime closingTime) {
            scheduleCalls++;
            failIfConfigured();
            lastManagerId = authenticatedUserId;
            lastExamId = examId;
            lastExamVersionNo = examVersionNo;
            lastOpeningTime = openingTime;
            lastClosingTime = closingTime;
            return executionEntity == null
                    ? execution(ExecutionStatus.SCHEDULED, openingTime, closingTime)
                    : executionEntity;
        }

        @Override
        public List<ExamExecutionSummaryDTO> findCreatedByManager(int authenticatedManagerId) {
            listCalls++;
            failIfConfigured();
            lastManagerId = authenticatedManagerId;
            return summaries;
        }

        @Override
        public Optional<ExamExecutionSummaryDTO> findByIdForManager(
                int authenticatedManagerId,
                int executionId
        ) {
            detailCalls++;
            failIfConfigured();
            lastManagerId = authenticatedManagerId;
            lastExecutionId = executionId;
            return Optional.ofNullable(detail);
        }

        @Override
        public Optional<ExamExecutionPreviewDTO> findByCodeForStudent(
                int authenticatedStudentId,
                String normalizedExecutionCode
        ) {
            codeCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentId;
            lastCode = normalizedExecutionCode;
            return Optional.ofNullable(preview);
        }

        @Override
        public Optional<ExamExecution> findEntityByCodeForStudent(
                int authenticatedStudentId,
                String executionCode
        ) {
            entityCodeCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentId;
            lastCode = executionCode;
            return Optional.ofNullable(executionEntity != null
                    ? executionEntity
                    : entityFromPreview());
        }

        @Override
        public Optional<ExamExecution> findEntityForStudent(
                int authenticatedStudentUserId,
                int executionId
        ) {
            entityStudentCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentUserId;
            lastExecutionId = executionId;
            return Optional.ofNullable(executionEntity != null
                    ? executionEntity
                    : execution(
                            ExecutionStatus.OPEN,
                            NOW.minusMinutes(1),
                            NOW.plusHours(1)
                    ));
        }

        int totalCalls() {
            return scheduleCalls + listCalls + detailCalls
                    + codeCalls + entityCodeCalls + entityStudentCalls;
        }

        private ExamExecution entityFromPreview() {
            return preview == null ? null : execution(
                    preview.getStatus(),
                    preview.getOpeningTime(),
                    preview.getClosingTime()
            );
        }

        private void failIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    static final class RecordingSubmissionRepository extends ExamSubmissionRepository {
        ExamAttemptDTO attempt = ExamExecutionServiceTestSupport.attempt(
                SubmissionStatus.IN_PROGRESS
        );
        List<ExamSubmission> expiredEntities = new ArrayList<>();
        int startCalls;
        int activeCalls;
        int activeEntityCalls;
        int studentEntityCalls;
        int managerEntityCalls;
        int persistAnswerCalls;
        int expiredEntityCalls;
        int persistStudentCalls;
        int persistAutomaticCalls;
        int persistExtensionCalls;
        int managerSummaryCalls;
        int managerReviewCalls;
        int publishedSummaryCalls;
        int publishedGradeCalls;
        int publishedReviewCalls;
        int persistReviewCalls;
        int persistPublicationCalls;
        int lastStudentId;
        int lastManagerId;
        int lastExecutionId;
        int lastSubmissionId;
        ExamExecution lastExecutionEntity;
        ExamSubmission lastSubmissionEntity;
        ExamSubmission lastPersistedStudentSubmission;
        ExamSubmission lastPersistedReview;
        ExamSubmission lastPersistedPublication;
        final List<ExamSubmission> persistedAutomaticSubmissions = new ArrayList<>();
        List<ExecutionSubmissionSummaryDTO> managerSummaries = new ArrayList<>();
        SubmissionReviewDTO managerReview;
        List<PublishedGradeSummaryDTO> publishedSummaries = new ArrayList<>();
        PublishedGradeDTO publishedGrade;
        PublishedExamReviewDTO publishedReview;
        StudentAnswer lastAnswerEntity;
        int lastAddedMinutes;
        String lastReason;
        LocalDateTime lastTime;
        boolean activePresent = true;
        boolean extensionResult = true;
        RuntimeException failure;
        RuntimeException persistStudentFailure;
        RuntimeException persistAutomaticFailure;
        RuntimeException persistReviewFailure;
        RuntimeException persistPublicationFailure;

        @Override
        public ExamSubmission startOrResume(int authenticatedStudentUserId,
                                            ExamExecution execution,
                                            LocalDateTime currentTime) {
            startCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentUserId;
            lastExecutionId = execution.getExecutionId();
            lastExecutionEntity = execution;
            lastTime = currentTime;
            return lastSubmissionEntity == null
                    ? submission(authenticatedStudentUserId, List.of())
                    : lastSubmissionEntity;
        }

        @Override
        public Optional<ExamAttemptDTO> findActiveForStudent(int authenticatedStudentId,
                                                              int submissionId,
                                                              LocalDateTime now) {
            activeCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentId;
            lastSubmissionId = submissionId;
            lastTime = now;
            return activePresent ? Optional.of(attempt) : Optional.empty();
        }

        @Override
        public Optional<ExamSubmission> findActiveEntityForStudent(
                int authenticatedStudentUserId,
                int submissionId
        ) {
            activeEntityCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentUserId;
            lastSubmissionId = submissionId;
            return activePresent
                    ? Optional.of(lastSubmissionEntity == null
                            ? submission(authenticatedStudentUserId, List.of())
                            : lastSubmissionEntity)
                    : Optional.empty();
        }

        @Override
        public Optional<ExamSubmission> findEntityForStudent(
                int authenticatedStudentUserId,
                int submissionId
        ) {
            studentEntityCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentUserId;
            lastSubmissionId = submissionId;
            return activePresent
                    ? Optional.of(lastSubmissionEntity == null
                            ? submission(authenticatedStudentUserId, List.of())
                            : lastSubmissionEntity)
                    : Optional.empty();
        }

        @Override
        public Optional<ExamSubmission> findEntityForManager(
                int authenticatedManagerUserId,
                int submissionId
        ) {
            managerEntityCalls++;
            failIfConfigured();
            lastManagerId = authenticatedManagerUserId;
            lastSubmissionId = submissionId;
            return extensionResult
                    ? Optional.of(lastSubmissionEntity == null
                            ? submission(1001, List.of())
                            : lastSubmissionEntity)
                    : Optional.empty();
        }

        @Override
        public List<ExecutionSubmissionSummaryDTO> findSummariesForManager(
                int authenticatedManagerUserId,
                int executionId
        ) {
            managerSummaryCalls++;
            failIfConfigured();
            lastManagerId = authenticatedManagerUserId;
            lastExecutionId = executionId;
            return managerSummaries;
        }

        @Override
        public Optional<SubmissionReviewDTO> findReviewForManager(
                int authenticatedManagerUserId,
                int submissionId
        ) {
            managerReviewCalls++;
            failIfConfigured();
            lastManagerId = authenticatedManagerUserId;
            lastSubmissionId = submissionId;
            return Optional.ofNullable(managerReview);
        }

        @Override
        public List<PublishedGradeSummaryDTO> findPublishedSummariesForStudent(
                int authenticatedStudentUserId
        ) {
            publishedSummaryCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentUserId;
            return publishedSummaries;
        }

        @Override
        public Optional<PublishedGradeDTO> findPublishedGradeForStudent(
                int authenticatedStudentUserId,
                int submissionId
        ) {
            publishedGradeCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentUserId;
            lastSubmissionId = submissionId;
            return Optional.ofNullable(publishedGrade);
        }

        @Override
        public Optional<PublishedExamReviewDTO> findPublishedExamReviewForStudent(
                int authenticatedStudentUserId,
                int submissionId
        ) {
            publishedReviewCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentUserId;
            lastSubmissionId = submissionId;
            return Optional.ofNullable(publishedReview);
        }

        @Override
        public ExamSubmission persistAnswer(
                int authenticatedStudentUserId,
                ExamSubmission submission,
                StudentAnswer answer,
                LocalDateTime currentTime
        ) {
            persistAnswerCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentUserId;
            lastSubmissionEntity = submission;
            lastAnswerEntity = answer;
            lastTime = currentTime;
            return submission;
        }

        @Override
        public List<ExamSubmission> findExpiredInProgressEntities(
                LocalDateTime currentTime
        ) {
            expiredEntityCalls++;
            failIfConfigured();
            lastTime = currentTime;
            return List.copyOf(expiredEntities);
        }

        @Override
        public ExamSubmission persistStudentSubmission(
                int authenticatedStudentUserId,
                ExamSubmission submission
        ) {
            persistStudentCalls++;
            failIfConfigured();
            if (persistStudentFailure != null) {
                throw persistStudentFailure;
            }
            lastStudentId = authenticatedStudentUserId;
            lastPersistedStudentSubmission = submission;
            lastSubmissionEntity = submission;
            return submission;
        }

        @Override
        public ExamSubmission persistAutomaticSubmission(
                ExamSubmission submission
        ) {
            persistAutomaticCalls++;
            failIfConfigured();
            if (persistAutomaticFailure != null) {
                throw persistAutomaticFailure;
            }
            persistedAutomaticSubmissions.add(submission);
            lastSubmissionEntity = submission;
            return submission;
        }

        @Override
        public ExamSubmission persistExtension(
                int authenticatedManagerUserId,
                ExamSubmission submission,
                int addedMinutes,
                String reason,
                LocalDateTime currentTime
        ) {
            persistExtensionCalls++;
            failIfConfigured();
            lastManagerId = authenticatedManagerUserId;
            lastSubmissionEntity = submission;
            lastAddedMinutes = addedMinutes;
            lastReason = reason;
            lastTime = currentTime;
            return submission;
        }

        @Override
        public ExamSubmission persistReview(int managerUserId,
                                             ExamSubmission submission) {
            persistReviewCalls++;
            failIfConfigured();
            if (persistReviewFailure != null) {
                throw persistReviewFailure;
            }
            lastManagerId = managerUserId;
            lastPersistedReview = submission;
            lastSubmissionEntity = submission;
            return submission;
        }

        @Override
        public ExamSubmission persistPublication(int managerUserId,
                                                  ExamSubmission submission) {
            persistPublicationCalls++;
            failIfConfigured();
            if (persistPublicationFailure != null) {
                throw persistPublicationFailure;
            }
            lastManagerId = managerUserId;
            lastPersistedPublication = submission;
            lastSubmissionEntity = submission;
            return submission;
        }

        int totalCalls() {
            return startCalls + activeCalls + activeEntityCalls
                    + studentEntityCalls + managerEntityCalls
                    + persistAnswerCalls
                    + expiredEntityCalls + persistStudentCalls
                    + persistAutomaticCalls + persistExtensionCalls
                    + managerSummaryCalls + managerReviewCalls
                    + publishedSummaryCalls + publishedGradeCalls + publishedReviewCalls
                    + persistReviewCalls + persistPublicationCalls;
        }

        private void failIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    static final class RecordingExamRepository extends ExamRepository {
        Exam exact = exactExam();
        int exactCalls;
        int lastExamId;
        int lastVersionNo;
        RuntimeException failure;

        @Override
        public Optional<Exam> findEntityVersion(int examId, int versionNo) {
            exactCalls++;
            if (failure != null) {
                throw failure;
            }
            lastExamId = examId;
            lastVersionNo = versionNo;
            return Optional.ofNullable(exact);
        }
    }

    static final class RecordingGradingService extends GradingService {
        int calls;
        int reviewCalls;
        int publicationCalls;
        int failAtCall;
        RuntimeException failure;
        RuntimeException reviewFailure;
        RuntimeException publicationFailure;
        ExamSubmission lastSubmission;
        ExamSubmission reviewResult;
        ExamSubmission publicationResult;
        Exam lastExam;
        int lastManagerId;
        BigDecimal lastFinalScore;
        String lastFeedback;
        String lastAdjustmentReason;
        LocalDateTime lastTime;

        @Override
        public ExamSubmission gradeAutomatically(
                ExamSubmission submission,
                Exam exactExamVersion,
                LocalDateTime gradedAt
        ) {
            calls++;
            lastSubmission = submission;
            lastExam = exactExamVersion;
            lastTime = gradedAt;
            if (failure != null && (failAtCall == 0 || failAtCall == calls)) {
                RuntimeException configured = failure;
                if (failAtCall != 0) {
                    failure = null;
                }
                throw configured;
            }
            return super.gradeAutomatically(submission, exactExamVersion, gradedAt);
        }

        @Override
        public ExamSubmission reviewGrade(
                ExamSubmission submission,
                int reviewerUserId,
                BigDecimal finalScore,
                String feedback,
                String adjustmentReason,
                LocalDateTime reviewedAt
        ) {
            reviewCalls++;
            lastSubmission = submission;
            lastManagerId = reviewerUserId;
            lastFinalScore = finalScore;
            lastFeedback = feedback;
            lastAdjustmentReason = adjustmentReason;
            lastTime = reviewedAt;
            if (reviewFailure != null) {
                throw reviewFailure;
            }
            if (reviewResult != null) {
                return reviewResult;
            }
            return super.reviewGrade(
                    submission,
                    reviewerUserId,
                    finalScore,
                    feedback,
                    adjustmentReason,
                    reviewedAt
            );
        }

        @Override
        public ExamSubmission publishGrade(
                ExamSubmission submission,
                int publisherUserId,
                LocalDateTime publishedAt
        ) {
            publicationCalls++;
            lastSubmission = submission;
            lastManagerId = publisherUserId;
            lastTime = publishedAt;
            if (publicationFailure != null) {
                throw publicationFailure;
            }
            if (publicationResult != null) {
                return publicationResult;
            }
            return super.publishGrade(submission, publisherUserId, publishedAt);
        }
    }

    static final class RecordingEnrollmentRepository extends StudentEnrollmentRepository {
        int readCalls;

        @Override
        public boolean isEnrolled(int authenticatedStudentId, int courseId) {
            readCalls++;
            return true;
        }
    }

    static final class RecordingProfileRepository extends StudentProfileRepository {
        boolean matches = true;
        int calls;
        int lastStudentId;
        String lastConfirmation;
        RuntimeException failure;

        @Override
        public boolean matchesIdentity(int authenticatedStudentId,
                                       String identityConfirmation) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            lastStudentId = authenticatedStudentId;
            lastConfirmation = identityConfirmation;
            return matches;
        }
    }
}
