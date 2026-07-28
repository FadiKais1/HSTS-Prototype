package hsts.client;

/**
 * Starts the JavaFX client from an ordinary main class so a shaded JAR does not
 * trigger the JDK's special JavaFX Application launcher.
 */
public final class ClientLauncher {
    private ClientLauncher() {
    }

    public static void main(String[] args) {
        MainClient.main(args);
    }
}
