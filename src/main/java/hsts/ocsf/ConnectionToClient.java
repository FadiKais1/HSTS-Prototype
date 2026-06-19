package hsts.ocsf;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ConnectionToClient implements Runnable, Closeable {
    private final Socket socket;
    private final AbstractServer server;

    private final ObjectOutputStream output;
    private final ObjectInputStream input;

    private final Map<String, Object> info = new HashMap<>();
    private volatile boolean connected = true;

    ConnectionToClient(Socket socket, AbstractServer server) throws IOException {
        this.socket = socket;
        this.server = server;

        this.output = new ObjectOutputStream(socket.getOutputStream());
        this.output.flush();

        this.input = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (connected) {
                Object message = input.readObject();
                server.receiveMessageFromClient(message, this);
            }
        } catch (Exception ignored) {
        } finally {
            connected = false;
            server.removeClient(this);

            try {
                close();
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void sendToClient(Object message) throws IOException {
        if (!connected) {
            throw new IOException("Client is not connected");
        }

        output.reset();
        output.writeObject(message);
        output.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        connected = false;

        try {
            input.close();
        } catch (IOException ignored) {
        }

        try {
            output.close();
        } catch (IOException ignored) {
        }

        socket.close();
    }

    public InetAddress getInetAddress() {
        return socket.getInetAddress();
    }

    public void setInfo(String key, Object value) {
        info.put(key, value);
    }

    public Object getInfo(String key) {
        return info.get(key);
    }

    @Override
    public String toString() {
        return "ConnectionToClient{" +
                "address=" + socket.getInetAddress() +
                ", port=" + socket.getPort() +
                '}';
    }
}