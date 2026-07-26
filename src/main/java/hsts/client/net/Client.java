package hsts.client.net;

import hsts.common.Request;
import hsts.common.Response;
import hsts.ocsf.AbstractClient;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Client extends AbstractClient {
    // COMPATIBILITY-ONLY: Preserves the working HSTSClient response timeout.
    private static final int RESPONSE_TIMEOUT_SECONDS = 15;

    private String host;
    private int port;
    private boolean connected;

    // COMPATIBILITY-ONLY: Preserves HSTSClient's synchronous response waiting.
    private final LinkedBlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();

    // COMPATIBILITY-ONLY: Preserves the working HSTSClient constructor and connection behavior.
    public Client(String host, int port) throws IOException {
        super(host, port);
        openConnection();
    }

    public void connect() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void disconnect() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    // COMPATIBILITY-ONLY: Approved return-type correction for the working client API.
    public synchronized Response sendRequest(Request request) {
        try {
            responseQueue.clear();

            sendToServer(request);

            Response response = responseQueue.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (response == null) {
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
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    // COMPATIBILITY-ONLY: OCSF delivers raw server messages through this callback.
    @Override
    protected void handleMessageFromServer(Object message) {
        if (message instanceof Response response) {
            responseQueue.offer(response);
        } else {
            responseQueue.offer(Response.error("Invalid response type from server"));
        }
    }

    // COMPATIBILITY-ONLY: Preserves HSTSClient's connection notification behavior.
    @Override
    protected void connectionEstablished() {
        System.out.println("Connected to HSTS server");
    }

    // COMPATIBILITY-ONLY: Preserves HSTSClient's disconnection notification behavior.
    @Override
    protected void connectionClosed() {
        System.out.println("Disconnected from HSTS server");
    }

    // COMPATIBILITY-ONLY: Preserves HSTSClient's asynchronous connection error behavior.
    @Override
    protected void connectionException(Exception exception) {
        responseQueue.offer(Response.error("Connection error: " + exception.getMessage()));
    }
}
