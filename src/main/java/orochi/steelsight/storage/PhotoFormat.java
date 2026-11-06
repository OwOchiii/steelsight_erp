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

    static String extensionOf(MultipartFile photo) throws IOException {
        byte[] head;
        try (InputStream in = photo.getInputStream()) {
            head = in.readNBytes(12);
        }
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) return ".jpg";
        if (startsWith(head, 0, 0x89, 'P', 'N', 'G')) return ".png";
        if (head.length == 12
                && "RIFF".equals(new String(Arrays.copyOfRange(head, 0, 4), StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(Arrays.copyOfRange(head, 8, 12), StandardCharsets.US_ASCII))) {
            return ".webp";
        }
        throw new BusinessRuleException("The photo must be a JPEG, PNG or WebP image");
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
