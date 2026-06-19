package hsts.client.net;

import hsts.common.Request;
import hsts.common.Response;
import hsts.ocsf.AbstractClient;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class HSTSClient extends AbstractClient {
    private static final int RESPONSE_TIMEOUT_SECONDS = 15;

    private final LinkedBlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();

    public HSTSClient(String host, int port) throws IOException {
        super(host, port);
        openConnection();
    }

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

    @Override
    protected void handleMessageFromServer(Object message) {
        if (message instanceof Response response) {
            responseQueue.offer(response);
        } else {
            responseQueue.offer(Response.error("Invalid response type from server"));
        }
    }

    @Override
    protected void connectionEstablished() {
        System.out.println("Connected to HSTS server");
    }

    @Override
    protected void connectionClosed() {
        System.out.println("Disconnected from HSTS server");
    }

    @Override
    protected void connectionException(Exception exception) {
        responseQueue.offer(Response.error("Connection error: " + exception.getMessage()));
    }
}