package hsts.ocsf;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractServer {
    private int port;
    private volatile boolean listening;

    private ServerSocket serverSocket;
    private final List<ConnectionToClient> clientConnections = new CopyOnWriteArrayList<>();

    protected AbstractServer(int port) {
        this.port = port;
    }

    public final void listen() throws IOException {
        listening = true;
        serverSocket = new ServerSocket(port);
        serverStarted();

        while (listening) {
            try {
                Socket socket = serverSocket.accept();

                ConnectionToClient connection = new ConnectionToClient(socket, this);
                clientConnections.add(connection);

                clientConnected(connection);

                Thread clientThread = new Thread(connection, "ocsf-connection-to-client");
                clientThread.setDaemon(true);
                clientThread.start();

            } catch (IOException e) {
                if (listening) {
                    listeningException(e);
                    throw e;
                }
            }
        }

        serverStopped();
    }

    public final void stopListening() {
        listening = false;

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public final void close() {
        stopListening();

        for (ConnectionToClient client : clientConnections) {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }

        clientConnections.clear();
        serverClosed();
    }

    public final void sendToAllClients(Object message) {
        for (ConnectionToClient client : clientConnections) {
            try {
                client.sendToClient(message);
            } catch (IOException e) {
                clientException(client, e);
            }
        }
    }

    final void receiveMessageFromClient(Object message, ConnectionToClient client) {
        handleMessageFromClient(message, client);
    }

    final void removeClient(ConnectionToClient client) {
        clientConnections.remove(client);
        clientDisconnected(client);
    }

    public boolean isListening() {
        return listening;
    }

    public List<ConnectionToClient> getClientConnections() {
        return List.copyOf(clientConnections);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    protected void serverStarted() {
    }

    protected void serverStopped() {
    }

    protected void serverClosed() {
    }

    protected void listeningException(Exception exception) {
    }

    protected void clientConnected(ConnectionToClient client) {
    }

    protected void clientDisconnected(ConnectionToClient client) {
    }

    protected void clientException(ConnectionToClient client, Exception exception) {
    }

    protected abstract void handleMessageFromClient(Object message, ConnectionToClient client);
}