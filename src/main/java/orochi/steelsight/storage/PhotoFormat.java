package orochi.steelsight.storage;

import org.springframework.web.multipart.MultipartFile;
import orochi.steelsight.exception.BusinessRuleException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** The file's own bytes decide its type, because the name and content type come from the browser and can say anything. */
final class PhotoFormat {

    private PhotoFormat() {
    }

    /**
     * Stored names are generated, so they never hold a folder. Refusing anything else means a bad value
     * in the database can't reach into another album or outside the storage folder.
     */
    /** An evidence photo's path as the detection app writes it: folders allowed, never "..". */
    static boolean isEvidencePath(String path) {
        return path != null && path.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,299}") && !path.contains("..") && !path.contains("//");
    }

    static boolean isAllowed(PhotoAlbum album, String name) {
        return album == PhotoAlbum.EVIDENCE ? isEvidencePath(name) : isStoredName(name);
    }

    static boolean isStoredName(String name) {
        return name != null && name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}") && !name.contains("..");
    }

    /** Checks the size first, then works out the type from the file's own bytes. */
    static String extensionOf(MultipartFile photo) throws IOException {
        if (photo.getSize() > PhotoStorage.MAX_BYTES) {
            throw new BusinessRuleException("The photo must be 5 MB or smaller");
        }
        byte[] head;
        try (InputStream in = photo.getInputStream()) {
            head = in.readNBytes(12);
        }
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) return ".jpg";
        if (startsWith(head, 0, 0x89, 'P', 'N', 'G')) return ".png";
        throw new BusinessRuleException("The photo must be JPEG or PNG, at most 5 MB and at least 480 x 480 pixels.");
    }

    /** SRS BR-09, MSG18: a face photo is at least 480 x 480 pixels, so the face is big enough to enrol. */
    static void checkFace(MultipartFile photo) throws IOException {
        java.awt.image.BufferedImage image;
        try (InputStream in = photo.getInputStream()) {
            image = javax.imageio.ImageIO.read(in);
        } catch (javax.imageio.IIOException e) {
            image = null;   // a damaged file is a photo we can't use, not a server error
        }
        if (image == null || image.getWidth() < 480 || image.getHeight() < 480) {
            throw new BusinessRuleException("The photo must be JPEG or PNG, at most 5 MB and at least 480 x 480 pixels.");
        }
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
