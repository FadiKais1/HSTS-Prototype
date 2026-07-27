package hsts.server.control;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import org.junit.Test;

import java.util.List;

import static hsts.server.control.ExamExecutionServiceTestSupport.NOW;
import static hsts.server.control.ExamExecutionServiceTestSupport.service;
import static hsts.server.control.ExamExecutionServiceTestSupport.user;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ExamExecutionServiceAutomationTest {
    @Test
    public void autoSubmitUsesOneInjectedTimeForAllIdsAndCountsOnlyTrueResults() {
        ExamExecutionServiceTestSupport.RecordingExecutionRepository executions =
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository();
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        submissions.expiredIds = List.of(501, 502, 503);
        submissions.autoResults.put(501, true);
        submissions.autoResults.put(502, false);
        submissions.autoResults.put(503, true);
        ExamExecutionService service = service(
                executions,
                submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                new ExamExecutionServiceTestSupport.RecordingProfileRepository(),
                new ExamExecutionServiceTestSupport.RecordingUserRepository(
                        user(1, UserRole.STUDENT, UserStatus.ACTIVE)
                )
        );

        assertEquals(2, service.autoSubmitExpired());
        assertEquals(1, submissions.expiredCalls);
        assertEquals(3, submissions.autoCalls);
        assertEquals(NOW, submissions.lastTime);
        assertEquals(List.of(NOW, NOW, NOW), submissions.autoTimes);
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
}
