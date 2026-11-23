package orochi.steelsight;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Real images for upload tests: a face photo must be JPEG or PNG and at least 480 x 480 pixels (SRS BR-09). */
public final class TestImages {

    public static final byte[] FACE_JPEG = image(480, 480, "jpg");
    public static final byte[] FACE_PNG = image(640, 480 + 160, "png");
    public static final byte[] SMALL_JPEG = image(200, 200, "jpg");

    private TestImages() {
    }

    public static byte[] image(int width, int height, String format) {
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(picture, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
