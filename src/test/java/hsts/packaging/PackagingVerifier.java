package hsts.packaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class PackagingVerifier {
    private static final String CLIENT_JAR = "G7_Client.jar";
    private static final String SERVER_JAR = "G7_Server.jar";

    private static final List<String> CLIENT_ENTRIES = List.of(
            "hsts/client/ClientLauncher.class",
            "hsts/client/MainClient.class",
            "hsts/ocsf/AbstractClient.class",
            "javafx/application/Application.class",
            "javafx/fxml/FXMLLoader.class",
            "javafx/scene/control/Button.class",
            "hsts/client/boundary/login-page.fxml",
            "hsts/client/boundary/student-dashboard.fxml",
            "hsts/client/boundary/teacher-dashboard.fxml",
            "hsts/client/boundary/principal-dashboard.fxml",
            "hsts/client/boundary/principal-oversight-page.fxml",
            "hsts/client/boundary/question-bank-page.fxml",
            "hsts/client/boundary/exam-builder-page.fxml",
            "hsts/client/boundary/approval-requests-page.fxml",
            "hsts/client/boundary/exam-scheduling-page.fxml",
            "hsts/client/boundary/exam-execution-page.fxml",
            "hsts/client/boundary/grade-review-page.fxml",
            "hsts/client/boundary/published-grades-page.fxml",
            "hsts/client/boundary/reports-page.fxml"
    );

    private static final List<String> SERVER_ENTRIES = List.of(
            "hsts/server/MainServer.class",
            "hsts/server/bot/source/BotSourceExtractor.class",
            "hsts/server/bot/GeminiExternalBotSystem.class",
            "hsts/server/bot/CourseBotProviderFactory.class",
            "com/fasterxml/jackson/databind/ObjectMapper.class",
            "hsts/ocsf/AbstractServer.class",
            "hsts/ocsf/ConnectionToClient.class",
            "com/mysql/cj/jdbc/Driver.class",
            "org/apache/pdfbox/text/PDFTextStripper.class",
            "org/apache/poi/xwpf/usermodel/XWPFDocument.class",
            "org/apache/xmlbeans/XmlObject.class",
            "org/apache/commons/compress/archivers/zip/ZipArchiveInputStream.class",
            "org/apache/commons/lang3/StringUtils.class",
            "org/apache/logging/slf4j/SLF4JProvider.class"
    );

    private static final List<String> CLIENT_FORBIDDEN_ENTRIES = List.of(
            "com/mysql/cj/jdbc/Driver.class",
            "org/apache/pdfbox/text/PDFTextStripper.class",
            "org/apache/poi/xwpf/usermodel/XWPFDocument.class",
            "org/apache/xmlbeans/XmlObject.class",
            "org/apache/commons/compress/archivers/zip/ZipArchiveInputStream.class",
            "org/apache/commons/lang3/StringUtils.class",
            "org/apache/logging/slf4j/SLF4JProvider.class"
            , "hsts/server/bot/GeminiExternalBotSystem.class"
            , "hsts/server/bot/CourseBotProviderFactory.class"
            , "com/fasterxml/jackson/databind/ObjectMapper.class"
    );

    private static final List<String> SERVER_FORBIDDEN_ENTRIES = List.of(
            "javafx/application/Application.class",
            "javafx/fxml/FXMLLoader.class"
    );

    private PackagingVerifier() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the Maven target directory");
        }

        Path targetDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Path clientPath = targetDirectory.resolve(CLIENT_JAR);
        Path serverPath = targetDirectory.resolve(SERVER_JAR);
        requireArtifact(clientPath);
        requireArtifact(serverPath);
        if (Files.isSameFile(clientPath, serverPath)) {
            throw new IllegalStateException("Client and server artifacts must be distinct");
        }

        verifyJar(
                clientPath,
                "hsts.client.ClientLauncher",
                CLIENT_ENTRIES,
                CLIENT_FORBIDDEN_ENTRIES,
                true,
                false
        );
        verifyJar(
                serverPath,
                "hsts.server.MainServer",
                SERVER_ENTRIES,
                SERVER_FORBIDDEN_ENTRIES,
                false,
                true
        );

        System.out.println("Verified packaged artifacts: " + CLIENT_JAR + ", " + SERVER_JAR);
    }

    private static void requireArtifact(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) == 0) {
            throw new IllegalStateException("Missing packaged artifact: " + path.getFileName());
        }
    }

    private static void verifyJar(
            Path path,
            String expectedMainClass,
            List<String> requiredEntries,
            List<String> forbiddenEntries,
            boolean requireWindowsNatives,
            boolean requireMysqlService
    ) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), true)) {
            if (jar.getManifest() == null) {
                throw new IllegalStateException(path.getFileName() + " has no manifest");
            }
            Attributes attributes = jar.getManifest().getMainAttributes();
            String mainClass = attributes.getValue(Attributes.Name.MAIN_CLASS);
            require(expectedMainClass.equals(mainClass),
                    path.getFileName() + " has wrong Main-Class: " + mainClass);
            auditManifest(path, jar.getManifest().getMainAttributes());

            Set<String> names = new HashSet<>();
            boolean hasWindowsNative = false;
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                names.add(name);
                String upperName = name.toUpperCase(Locale.ROOT);
                require(!upperName.endsWith(".SF")
                                && !upperName.endsWith(".RSA")
                                && !upperName.endsWith(".DSA"),
                        path.getFileName() + " contains signature metadata: " + name);
                require(!name.startsWith("docs/Project Reference/")
                                && !name.startsWith(".git/")
                                && !name.startsWith("target/"),
                        path.getFileName() + " contains prohibited content: " + name);
                require(!name.endsWith(".jar"),
                        path.getFileName() + " contains a recursive JAR: " + name);
                require(!name.startsWith("hsts/packaging/"),
                        path.getFileName() + " contains packaging test classes");
                if (upperName.endsWith(".DLL")) {
                    hasWindowsNative = true;
                }
            }

            for (String requiredEntry : requiredEntries) {
                require(names.contains(requiredEntry),
                        path.getFileName() + " is missing " + requiredEntry);
            }
            for (String forbiddenEntry : forbiddenEntries) {
                require(!names.contains(forbiddenEntry),
                        path.getFileName() + " unexpectedly contains " + forbiddenEntry);
            }
            if (requireWindowsNatives) {
                require(hasWindowsNative,
                        path.getFileName() + " contains no JavaFX Windows native libraries");
            }
            if (requireMysqlService) {
                require(names.contains("META-INF/services/java.sql.Driver"),
                        path.getFileName() + " is missing the JDBC service resource");
            }
        }
    }

    private static void auditManifest(Path path, Attributes attributes) {
        String manifestText = attributes.entrySet().toString();
        String lowerText = manifestText.toLowerCase(Locale.ROOT);
        require(!manifestText.contains("C:\\")
                        && !lowerText.contains(".m2")
                        && !lowerText.contains("password"),
                path.getFileName() + " manifest contains a local path or credential field");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
