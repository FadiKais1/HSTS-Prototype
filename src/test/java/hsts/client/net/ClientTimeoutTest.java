package hsts.client.net;

import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import org.junit.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ClientTimeoutTest {
    @Test
    public void botRequestCanCompleteAfterOrdinaryTimeoutWithoutRetry() throws Exception {
        try (ScriptedServer server = new ScriptedServer((input, output, requests) -> {
            requests.add((Request) input.readObject());
            Thread.sleep(140);
            output.writeObject(Response.success("Bot answered", "answer"));
            output.flush();
        });
             Client client = new Client("localhost", server.port(),
                     Duration.ofMillis(50), Duration.ofMillis(500))) {
            Response response = client.sendCourseBotRequest(
                    new Request(RequestType.ASK_COURSE_BOT, "question")
            );

            assertTrue(response.isSuccess());
            assertEquals("answer", response.getPayload());
            assertEquals(1, server.requests().size());
        }
    }

    @Test
    public void lateTimedOutResponseCannotCompleteTheNextRequest() throws Exception {
        try (ScriptedServer server = new ScriptedServer((input, output, requests) -> {
            requests.add((Request) input.readObject());
            Thread.sleep(150);
            output.writeObject(Response.success("late", "stale"));
            output.flush();
            requests.add((Request) input.readObject());
            output.writeObject(Response.success("current", "fresh"));
            output.flush();
        });
             Client client = new Client("localhost", server.port(),
                     Duration.ofMillis(100), Duration.ofMillis(500))) {
            Response timedOut = client.sendRequest(new Request(RequestType.GET_MY_COURSES, null));
            Response next = client.sendRequest(new Request(RequestType.GET_MY_COURSES, null));

            assertFalse(timedOut.isSuccess());
            assertEquals("Server response timed out", timedOut.getMessage());
            assertTrue(next.isSuccess());
            assertEquals("fresh", next.getPayload());
            assertEquals(2, server.requests().size());
        }
    }

    @Test
    public void dedicatedApiRejectsNonBotRequestTypes() throws Exception {
        try (ScriptedServer server = new ScriptedServer((input, output, requests) -> {
        });
             Client client = new Client("localhost", server.port(),
                     Duration.ofMillis(50), Duration.ofMillis(500))) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> client.sendCourseBotRequest(
                            new Request(RequestType.GET_MY_COURSES, null)
                    ));
            assertEquals("Course Bot request type is required", failure.getMessage());
            assertTrue(server.requests().isEmpty());
        }
    }

    private interface Script {
        void run(ObjectInputStream input, ObjectOutputStream output, List<Request> requests)
                throws Exception;
    }

    private static final class ScriptedServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final Thread thread;

        private ScriptedServer(Script script) throws Exception {
            serverSocket = new ServerSocket(0);
            thread = new Thread(() -> {
                try (Socket socket = serverSocket.accept();
                     ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                    output.flush();
                    try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                        script.run(input, output, requests);
                    }
                } catch (Exception ignored) {
                    // Closing a test client/server is expected to end the scripted peer.
                }
            }, "client-timeout-test-server");
            thread.setDaemon(true);
            thread.start();
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private List<Request> requests() {
            return new ArrayList<>(requests);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(1_000);
        }
    }
}
