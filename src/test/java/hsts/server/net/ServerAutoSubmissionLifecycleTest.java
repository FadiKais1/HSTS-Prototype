package hsts.server.net;

import hsts.server.control.AuthService;
import hsts.server.control.ExamExecutionService;
import hsts.server.control.ExamManagementService;
import hsts.server.support.InMemoryQuestionRepository;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ServerAutoSubmissionLifecycleTest {
    @Test
    public void oneCycleCallsServiceAndFailureDoesNotPreventLaterCycle() {
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        Server server = server(service);

        service.failNext = true;
        server.runAutoSubmissionCycle();
        server.runAutoSubmissionCycle();

        assertEquals(2, service.autoSubmitCalls);
    }

    @Test
    public void repeatedStartUsesOneSchedulerAndStopAllowsFreshRestart()
            throws ReflectiveOperationException {
        Server server = server(new RecordingExamExecutionService());
        ScheduledExecutorService first = null;
        ScheduledExecutorService second = null;

        try {
            server.serverStarted();
            first = scheduler(server);
            assertNotNull(first);

            server.serverStarted();
            assertSame(first, scheduler(server));

            server.serverStopped();
            assertTrue(first.isShutdown());
            assertNull(scheduler(server));

            server.serverStarted();
            second = scheduler(server);
            assertNotNull(second);
            assertNotSame(first, second);

            server.serverClosed();
            assertTrue(second.isShutdown());
            assertNull(scheduler(server));
        } finally {
            server.serverClosed();
        }
    }

    @Test
    public void missingExecutionServiceNeverStartsScheduler()
            throws ReflectiveOperationException {
        Server server = new Server(
                0,
                new ExamManagementService(new InMemoryQuestionRepository()),
                new AuthService(new InMemoryUserRepository())
        );

        try {
            server.serverStarted();
            assertNull(scheduler(server));
            server.runAutoSubmissionCycle();
        } finally {
            server.serverClosed();
        }
    }

    private static Server server(ExamExecutionService service) {
        return new Server(
                0,
                new ExamManagementService(new InMemoryQuestionRepository()),
                new AuthService(new InMemoryUserRepository()),
                service
        );
    }

    private static ScheduledExecutorService scheduler(Server server)
            throws ReflectiveOperationException {
        Field field = Server.class.getDeclaredField("autoSubmissionScheduler");
        field.setAccessible(true);
        return (ScheduledExecutorService) field.get(server);
    }

    private static final class RecordingExamExecutionService
            extends ExamExecutionService {
        private int autoSubmitCalls;
        private boolean failNext;

        @Override
        public int autoSubmitExpired() {
            autoSubmitCalls++;
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("temporary failure");
            }
            return 1;
        }
    }
}
