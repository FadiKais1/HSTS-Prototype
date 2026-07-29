package hsts.client.boundary;

import org.junit.Test;

import hsts.common.QuestionIllustrationDTO;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class QuestionIllustrationIntegrationTest {
    private static final List<String> PAGES = List.of(
            "question-bank-page.fxml", "exam-builder-page.fxml",
            "approval-requests-page.fxml", "exam-execution-page.fxml",
            "grade-review-page.fxml", "published-grades-page.fxml",
            "principal-oversight-page.fxml"
    );

    @Test
    public void allSevenPagesUseBoundedInMemoryIllustrationViews() throws Exception {
        for (String page : PAGES) {
            String fxml = Files.readString(Path.of(
                    "src/main/resources/hsts/client/boundary", page
            ));
            assertTrue(page, fxml.contains("ImageView"));
            assertTrue(page, fxml.contains("preserveRatio=\"true\""));
            assertTrue(page, fxml.contains("fitWidth="));
            assertTrue(page, fxml.contains("fitHeight="));
            assertFalse(page, fxml.contains("illustrationPath"));
            assertFalse(page, fxml.contains("image=\"http://"));
            assertFalse(page, fxml.contains("image=\"https://"));
            assertFalse(page, fxml.contains("image=\"file:"));
        }
    }

    @Test
    public void rendererContainsNoFilesystemOrExternalUrlLoader() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/hsts/client/boundary/QuestionIllustrationRenderer.java"
        ));
        assertFalse(source.contains("java.nio.file"));
        assertFalse(source.contains("new URL"));
        assertFalse(source.contains("getIllustrationPath"));
        assertTrue(source.contains("new ByteArrayInputStream(bytes)"));
        assertTrue(source.contains("generation"));
        assertTrue(source.contains("MAX_PIXELS"));
    }

    @Test
    public void inMemoryPngDecodesWithoutPathOrUrl() throws Exception {
        byte[] bytes = png();
        QuestionIllustrationDTO dto = new QuestionIllustrationDTO(
                "image/png", bytes, bytes.length, 4, 3, checksum(bytes)
        );
        var image = QuestionIllustrationRenderer.decode(dto);
        assertFalse(image.isError());
        assertEquals(4.0, image.getWidth(), 0.0);
        assertEquals(3.0, image.getHeight(), 0.0);
    }

    @Test
    public void malformedInMemoryBytesFailClosed() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        QuestionIllustrationDTO dto = new QuestionIllustrationDTO(
                "image/png", bytes, bytes.length, 1, 1, checksum(bytes)
        );
        assertEquals(QuestionIllustrationRenderer.UNAVAILABLE_MESSAGE,
                assertThrows(IllegalArgumentException.class,
                        () -> QuestionIllustrationRenderer.decode(dto)).getMessage());
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        return bytes.toByteArray();
    }

    private static String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
        );
    }
}
