package hsts.server.control;

import hsts.common.ExamDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PrincipalQuestionDTO;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.UserStatus;
import hsts.server.entity.Principal;
import hsts.server.entity.Student;
import hsts.server.repository.ExamExecutionRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.ExamSubmissionRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PrincipalOversightServiceTest {
    @Test
    public void activePrincipalCanReadEveryGlobalProjection() {
        FakeQuestionRepository questions = new FakeQuestionRepository();
        FakeExamRepository exams = new FakeExamRepository();
        FakeExecutionRepository executions = new FakeExecutionRepository();
        FakeSubmissionRepository submissions = new FakeSubmissionRepository();
        PrincipalOversightService service = service(
                activePrincipal(), questions, exams, executions, submissions
        );

        assertEquals(0, service.getAllQuestions(1004).size());
        assertEquals(0, service.getAllExams(1004).size());
        assertEquals(0, service.getAllExecutions(1004).size());
        assertEquals(0, service.getExecutionResults(1004, 7).size());
        assertEquals(1, questions.allCalls);
        assertEquals(1, exams.allCalls);
        assertEquals(1, executions.allCalls);
        assertEquals(1, submissions.summaryCalls);
    }

    @Test
    public void nonPrincipalBlockedAndMissingUsersAreRejectedBeforeReads() {
        FakeQuestionRepository questions = new FakeQuestionRepository();
        PrincipalOversightService student = service(
                Student.rehydrate(1001, "Student", "s@local", "hash", UserStatus.ACTIVE),
                questions, new FakeExamRepository(), new FakeExecutionRepository(),
                new FakeSubmissionRepository()
        );
        assertMessage("Principal access required",
                () -> student.getAllQuestions(1001));
        assertEquals(0, questions.allCalls);

        Principal blocked = Principal.rehydrate(
                1004, "Principal", "p@local", "hash", UserStatus.BLOCKED
        );
        assertMessage("User account is blocked",
                () -> service(blocked, questions, new FakeExamRepository(),
                        new FakeExecutionRepository(), new FakeSubmissionRepository())
                        .getAllQuestions(1004));

        assertMessage("User not found: 9999",
                () -> service(null, questions, new FakeExamRepository(),
                        new FakeExecutionRepository(), new FakeSubmissionRepository())
                        .getAllQuestions(9999));
    }

    @Test
    public void missingHistoricalRecordsUseConcealedMessage() {
        PrincipalOversightService service = service(
                activePrincipal(), new FakeQuestionRepository(),
                new FakeExamRepository(), new FakeExecutionRepository(),
                new FakeSubmissionRepository()
        );
        assertMessage("Record not found or access denied",
                () -> service.getQuestionVersion(1004, 11, 2));
        assertMessage("Record not found or access denied",
                () -> service.getExamVersion(1004, 4, 2));
        assertMessage("Record not found or access denied",
                () -> service.getSubmissionResult(1004, 9));
    }

    private static PrincipalOversightService service(
            hsts.server.entity.User user, QuestionRepository questions,
            ExamRepository exams, ExamExecutionRepository executions,
            ExamSubmissionRepository submissions
    ) {
        InMemoryUserRepository users = user == null
                ? new InMemoryUserRepository() : new InMemoryUserRepository(user);
        return new PrincipalOversightService(
                users, questions, exams, executions, submissions
        );
    }

    private static Principal activePrincipal() {
        return Principal.rehydrate(
                1004, "Principal", "p@local", "hash", UserStatus.ACTIVE
        );
    }

    private static void assertMessage(String message, Runnable operation) {
        RuntimeException failure = assertThrows(RuntimeException.class, operation::run);
        assertEquals(message, failure.getMessage());
    }

    private static final class FakeQuestionRepository extends QuestionRepository {
        int allCalls;
        @Override public List<PrincipalQuestionDTO> findAllForPrincipal() {
            allCalls++;
            return List.of();
        }
        @Override public List<PrincipalQuestionDTO> findVersionsForPrincipal(int id) {
            return List.of();
        }
        @Override public Optional<PrincipalQuestionDTO> findVersionForPrincipal(int id, int v) {
            return Optional.empty();
        }
    }

    private static final class FakeExamRepository extends ExamRepository {
        int allCalls;
        @Override public List<ExamSummaryDTO> findAllForPrincipal() {
            allCalls++;
            return List.of();
        }
        @Override public List<ExamSummaryDTO> findVersionsForPrincipal(int id) {
            return List.of();
        }
        @Override public Optional<ExamDTO> findVersionForPrincipal(int id, int v) {
            return Optional.empty();
        }
    }

    private static final class FakeExecutionRepository extends ExamExecutionRepository {
        int allCalls;
        @Override public List<ExamExecutionSummaryDTO> findAllForPrincipal() {
            allCalls++;
            return List.of();
        }
    }

    private static final class FakeSubmissionRepository extends ExamSubmissionRepository {
        int summaryCalls;
        @Override public List<ExecutionSubmissionSummaryDTO> findSummariesForPrincipal(int id) {
            summaryCalls++;
            return List.of();
        }
        @Override public Optional<SubmissionReviewDTO> findReviewForPrincipal(int id) {
            return Optional.empty();
        }
    }
}
