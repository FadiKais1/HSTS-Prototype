package hsts.client.net;

import hsts.common.Request;
import hsts.common.Response;
import hsts.ocsf.AbstractClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Client extends AbstractClient {
    // COMPATIBILITY-ONLY: Preserves the working HSTSClient response timeout.
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration COURSE_BOT_RESPONSE_TIMEOUT = Duration.ofSeconds(45);

    private String host;
    private int port;
    private boolean connected;

    // COMPATIBILITY-ONLY: Preserves HSTSClient's synchronous response waiting.
    private final LinkedBlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger staleResponsesToDiscard = new AtomicInteger();
    private final Duration defaultResponseTimeout;
    private final Duration courseBotResponseTimeout;

    // COMPATIBILITY-ONLY: Preserves the working HSTSClient constructor and connection behavior.
    public Client(String host, int port) throws IOException {
        this(host, port, DEFAULT_RESPONSE_TIMEOUT, COURSE_BOT_RESPONSE_TIMEOUT);
    }

    Client(
            String host, int port, Duration defaultResponseTimeout,
            Duration courseBotResponseTimeout
    ) throws IOException {
        super(host, port);
        this.host = host;
        this.port = port;
        this.defaultResponseTimeout = requirePositiveTimeout(
                defaultResponseTimeout, "Default response timeout"
        );
        this.courseBotResponseTimeout = requirePositiveTimeout(
                courseBotResponseTimeout, "Course Bot response timeout"
        );
        openConnection();
    }

    public void connect() {
        if (isConnected()) {
            return;
        }

        try {
            openConnection();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to connect to server", e);
        }
    }

    public void disconnect() {
        if (!isConnected()) {
            return;
        }

        try {
            closeConnection();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to disconnect from server", e);
        }
    }

    // COMPATIBILITY-ONLY: Approved return-type correction for the working client API.
    public synchronized Response sendRequest(Request request) {
        return sendRequestWithTimeout(request, defaultResponseTimeout);
    }

    // COMPATIBILITY-ONLY: Course Bot provider calls may outlive the ordinary timeout.
    public synchronized Response sendCourseBotRequest(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.getType() != hsts.common.RequestType.ASK_COURSE_BOT) {
            throw new IllegalArgumentException("Course Bot request type is required");
        }
        return sendRequestWithTimeout(request, courseBotResponseTimeout);
    }

    private Response sendRequestWithTimeout(Request request, Duration timeout) {
        try {
            discardQueuedStaleResponses();
            responseQueue.clear();

            sendToServer(request);

            Response response = responseQueue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);

            if (response == null) {
                staleResponsesToDiscard.incrementAndGet();
                return Response.error("Server response timed out");
            }

            return response;

        } catch (IOException e) {
            return Response.error("Failed to send request to server: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.error("Request was interrupted");
        }
    }

    public void handleResponse(Response response) {
        if (discardOneStaleResponse()) {
            return;
        }
        responseQueue.offer(response);
    }

    // COMPATIBILITY-ONLY: OCSF delivers raw server messages through this callback.
    @Override
    protected void handleMessageFromServer(Object message) {
        if (message instanceof Response response) {
            handleResponse(response);
        } else {
            handleResponse(Response.error("Invalid response type from server"));
        }
    }

    private void discardQueuedStaleResponses() {
        while (staleResponsesToDiscard.get() > 0 && responseQueue.poll() != null) {
            discardOneStaleResponse();
        }
    }

    private boolean discardOneStaleResponse() {
        int pending = staleResponsesToDiscard.get();
        while (pending > 0) {
            if (staleResponsesToDiscard.compareAndSet(pending, pending - 1)) {
                return true;
            }
            pending = staleResponsesToDiscard.get();
        }
        return false;
    }

    private static Duration requirePositiveTimeout(Duration timeout, String label) {
        Objects.requireNonNull(timeout, label);
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return timeout;
    }

    // COMPATIBILITY-ONLY: Preserves HSTSClient's connection notification behavior.
    @Override
    protected void connectionEstablished() {
        connected = true;
        System.out.println("Connected to HSTS server");
    }

    // COMPATIBILITY-ONLY: Preserves HSTSClient's disconnection notification behavior.
    @Override
    protected void connectionClosed() {
        connected = false;
        System.out.println("Disconnected from HSTS server");
    }

    // COMPATIBILITY-ONLY: Preserves HSTSClient's asynchronous connection error behavior.
    @Override
    protected void connectionException(Exception exception) {
        responseQueue.offer(Response.error("Connection error: " + exception.getMessage()));
    }
}
