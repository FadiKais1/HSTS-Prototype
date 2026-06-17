package hsts.client.net;

import hsts.common.Request;
import hsts.common.Response;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class HSTSClient implements Closeable {
    private final Socket socket;
    private final ObjectOutputStream output;
    private final ObjectInputStream input;

    public HSTSClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.output = new ObjectOutputStream(socket.getOutputStream());
        this.output.flush();
        this.input = new ObjectInputStream(socket.getInputStream());
    }

    public synchronized Response sendRequest(Request request) {
        try {
            output.writeObject(request);
            output.flush();

            Object response = input.readObject();
            if (response instanceof Response typedResponse) {
                return typedResponse;
            }

            return Response.error("Invalid response from server");
        } catch (IOException | ClassNotFoundException e) {
            return Response.error("Failed to communicate with server: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
        output.close();
        socket.close();
    }
}
