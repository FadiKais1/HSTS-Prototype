package hsts.ocsf;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public abstract class AbstractClient implements Runnable, Closeable {
    private String host;
    private int port;

    private Socket clientSocket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    private Thread clientReaderThread;
    private volatile boolean connected;

    protected AbstractClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public final void openConnection() throws IOException {
        if (connected) {
            return;
        }

        clientSocket = new Socket(host, port);

        output = new ObjectOutputStream(clientSocket.getOutputStream());
        output.flush();

        input = new ObjectInputStream(clientSocket.getInputStream());

        connected = true;
        connectionEstablished();

        clientReaderThread = new Thread(this, "ocsf-client-reader");
        clientReaderThread.setDaemon(true);
        clientReaderThread.start();
    }

    public final synchronized void sendToServer(Object message) throws IOException {
        if (!connected || output == null) {
            throw new IOException("Client is not connected to server");
        }

        output.reset();
        output.writeObject(message);
        output.flush();
    }

    @Override
    public final void run() {
        try {
            while (connected) {
                Object message = input.readObject();
                handleMessageFromServer(message);
            }
        } catch (Exception e) {
            if (connected) {
                connectionException(e);
            }
        } finally {
            try {
                closeConnection();
            } catch (IOException ignored) {
            }
        }
    }

    public final synchronized void closeConnection() throws IOException {
        connected = false;

        IOException exception = null;

        try {
            if (input != null) {
                input.close();
            }
        } catch (IOException e) {
            exception = e;
        }

        try {
            if (output != null) {
                output.close();
            }
        } catch (IOException e) {
            exception = e;
        }

        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (IOException e) {
            exception = e;
        }

        connectionClosed();

        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public final void close() throws IOException {
        closeConnection();
    }

    public boolean isConnected() {
        return connected;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    protected void connectionEstablished() {
    }

    protected void connectionClosed() {
    }

    protected void connectionException(Exception exception) {
    }

    protected abstract void handleMessageFromServer(Object message);
}