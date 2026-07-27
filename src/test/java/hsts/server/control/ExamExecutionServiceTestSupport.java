package hsts.server.control;

import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.StudentExamQuestionDTO;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.Coordinator;
import hsts.server.entity.Principal;
import hsts.server.entity.Student;
import hsts.server.entity.Teacher;
import hsts.server.entity.User;
import hsts.server.repository.ExamExecutionRepository;
import hsts.server.repository.ExamSubmissionRepository;
import hsts.server.repository.StudentEnrollmentRepository;
import hsts.server.repository.StudentProfileRepository;
import hsts.server.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        return new ExamExecutionService(
                executions,
                submissions,
                enrollments,
                profiles,
                users,
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
        int createCalls;
        int listCalls;
        int detailCalls;
        int codeCalls;
        int lastManagerId;
        int lastExecutionId;
        int lastStudentId;
        String lastCode;
        ScheduleExamExecutionPayload lastSchedulePayload;
        List<ExamExecutionSummaryDTO> summaries = new ArrayList<>();
        ExamExecutionSummaryDTO detail;
        ExamExecutionPreviewDTO preview;
        RuntimeException failure;

        @Override
        public int create(int authenticatedManagerId, ScheduleExamExecutionPayload payload) {
            createCalls++;
            failIfConfigured();
            lastManagerId = authenticatedManagerId;
            lastSchedulePayload = payload;
            return createdId;
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

        int totalCalls() {
            return createCalls + listCalls + detailCalls + codeCalls;
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
        StudentAnswerDTO answer = new StudentAnswerDTO(17, 2, NOW);
        List<Integer> expiredIds = new ArrayList<>();
        final Map<Integer, Boolean> autoResults = new LinkedHashMap<>();
        int startCalls;
        int activeCalls;
        int saveCalls;
        int submitCalls;
        int expiredCalls;
        int autoCalls;
        int extensionCalls;
        int lastStudentId;
        int lastManagerId;
        int lastExecutionId;
        int lastSubmissionId;
        SaveExamAnswerPayload lastAnswerPayload;
        ExtendSubmissionTimePayload lastExtensionPayload;
        LocalDateTime lastTime;
        final List<LocalDateTime> autoTimes = new ArrayList<>();
        boolean activePresent = true;
        boolean extensionResult = true;
        RuntimeException failure;

        @Override
        public ExamAttemptDTO startOrResume(int authenticatedStudentId, int executionId,
                                            LocalDateTime now) {
            startCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentId;
            lastExecutionId = executionId;
            lastTime = now;
            return attempt;
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
        public StudentAnswerDTO saveAnswer(int authenticatedStudentId,
                                           SaveExamAnswerPayload payload,
                                           LocalDateTime now) {
            saveCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentId;
            lastAnswerPayload = payload;
            lastTime = now;
            return answer;
        }

        @Override
        public ExamAttemptDTO submit(int authenticatedStudentId, int submissionId,
                                     LocalDateTime now) {
            submitCalls++;
            failIfConfigured();
            lastStudentId = authenticatedStudentId;
            lastSubmissionId = submissionId;
            lastTime = now;
            return attempt;
        }

        @Override
        public List<Integer> findExpiredSubmissionIds(LocalDateTime now) {
            expiredCalls++;
            failIfConfigured();
            lastTime = now;
            return expiredIds;
        }

        @Override
        public boolean autoSubmit(int submissionId, LocalDateTime now) {
            autoCalls++;
            failIfConfigured();
            lastSubmissionId = submissionId;
            autoTimes.add(now);
            return autoResults.getOrDefault(submissionId, false);
        }

        @Override
        public boolean extendTime(int authenticatedManagerId,
                                  ExtendSubmissionTimePayload payload,
                                  LocalDateTime now) {
            extensionCalls++;
            failIfConfigured();
            lastManagerId = authenticatedManagerId;
            lastExtensionPayload = payload;
            lastTime = now;
            return extensionResult;
        }

        int totalCalls() {
            return startCalls + activeCalls + saveCalls + submitCalls
                    + expiredCalls + autoCalls + extensionCalls;
        }

        private void failIfConfigured() {
            if (failure != null) {
                throw failure;
            }
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
