package hsts.server.control;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.ExamSubmission;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static hsts.server.control.ExamExecutionServiceTestSupport.NOW;
import static hsts.server.control.ExamExecutionServiceTestSupport.service;
import static hsts.server.control.ExamExecutionServiceTestSupport.user;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ExamExecutionServiceAutomationTest {
    @Test
    public void autoSubmitUsesEntitiesExactVersionsAndOneInjectedTime() {
        ExamExecutionServiceTestSupport.RecordingExecutionRepository executions =
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository();
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        submissions.expiredEntities = new ArrayList<>(List.of(
                expiredSubmission(501, 1001, 3),
                ExamExecutionServiceTestSupport.submission(
                        502, 1002, NOW.minusMinutes(5), List.of()
                ),
                expiredSubmission(503, 1003, null)
        ));
        ExamExecutionServiceTestSupport.RecordingExamRepository exams =
                new ExamExecutionServiceTestSupport.RecordingExamRepository();
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
        ExamExecutionService service = service(
                executions,
                submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                new ExamExecutionServiceTestSupport.RecordingProfileRepository(),
                new ExamExecutionServiceTestSupport.RecordingUserRepository(
                        user(1, UserRole.STUDENT, UserStatus.ACTIVE)
                ),
                exams,
                grading
        );

        assertEquals(2, service.autoSubmitExpired());
        assertEquals(1, submissions.expiredEntityCalls);
        assertEquals(2, submissions.persistAutomaticCalls);
        assertEquals(0, submissions.expiredCalls);
        assertEquals(0, submissions.autoCalls);
        assertEquals(NOW, submissions.lastTime);
        assertEquals(2, grading.calls);
        assertEquals(NOW, grading.lastTime);
        assertEquals(2, exams.exactCalls);
        assertEquals(
                List.of(501, 503),
                submissions.persistedAutomaticSubmissions.stream()
                        .map(ExamSubmission::getSubmissionId)
                        .toList()
        );
        assertEquals(
                "100.00",
                submissions.persistedAutomaticSubmissions.get(0)
                        .getAutomaticScoreValue().orElseThrow().toPlainString()
        );
        assertEquals(
                "0.00",
                submissions.persistedAutomaticSubmissions.get(1)
                        .getAutomaticScoreValue().orElseThrow().toPlainString()
        );
    }

    @Test
    public void autoSubmitInfrastructureFailurePropagatesAndLegacyDependenciesFailClearly() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        RuntimeException failure = new IllegalStateException("submission scan failure");
        submissions.failure = failure;
        ExamExecutionService service = service(
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository(),
                submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                new ExamExecutionServiceTestSupport.RecordingProfileRepository(),
                new ExamExecutionServiceTestSupport.RecordingUserRepository()
        );

        assertSame(failure, assertThrows(
                RuntimeException.class,
                service::autoSubmitExpired
        ));

        IllegalStateException missing = assertThrows(
                IllegalStateException.class,
                () -> new ExamExecutionService().autoSubmitExpired()
        );
        assertEquals(
                "Exam execution dependencies are not configured",
                missing.getMessage()
        );
    }

    @Test
    public void failedSubmissionStopsCurrentCycleButLaterCycleCanContinue() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        submissions.expiredEntities = new ArrayList<>(List.of(
                expiredSubmission(501, 1001, 3),
                expiredSubmission(502, 1002, 2),
                expiredSubmission(503, 1003, null)
        ));
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
        RuntimeException failure = new IllegalStateException("grading failure");
        grading.failure = failure;
        grading.failAtCall = 2;
        ExamExecutionService service = service(
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository(),
                submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                new ExamExecutionServiceTestSupport.RecordingProfileRepository(),
                new ExamExecutionServiceTestSupport.RecordingUserRepository(),
                new ExamExecutionServiceTestSupport.RecordingExamRepository(),
                grading
        );

        assertSame(failure, assertThrows(RuntimeException.class, service::autoSubmitExpired));
        assertEquals(1, submissions.persistAutomaticCalls);
        assertEquals(501, submissions.persistedAutomaticSubmissions.get(0).getSubmissionId());

        submissions.expiredEntities = new ArrayList<>(List.of(
                expiredSubmission(502, 1002, 2),
                expiredSubmission(503, 1003, null)
        ));
        assertEquals(2, service.autoSubmitExpired());
        assertEquals(3, submissions.persistAutomaticCalls);
    }

    private static ExamSubmission expiredSubmission(
            int submissionId,
            int studentId,
            Integer selectedOption
    ) {
        java.time.LocalDateTime started = NOW.minusHours(2);
        return ExamExecutionServiceTestSupport.submission(
                submissionId,
                studentId,
                started,
                selectedOption == null
                        ? List.of()
                        : List.of(ExamExecutionServiceTestSupport.answer(
                                submissionId + 1000,
                                submissionId,
                                selectedOption,
                                started.plusMinutes(10)
                        ))
        );
    }
}
