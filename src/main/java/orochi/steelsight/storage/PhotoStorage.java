package orochi.steelsight.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/**
 * Keeps ticket photos on local disk. The file's own bytes decide its type, because the name and
 * content type come from the browser and can say anything. Stored names are random.
 */
@Component
public class PhotoStorage {

    private final Path root;

    public PhotoStorage(@Value("${steelsight.storage.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /** Returns the stored path, or null when no photo was chosen. */
    public String store(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        try {
            String extension = extensionOf(photo);
            String name = UUID.randomUUID() + extension;
            Files.createDirectories(root);
            try (InputStream in = photo.getInputStream()) {
                Files.copy(in, root.resolve(name));
            }
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Resource load(String storedPath) {
        Path file = resolve(storedPath);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("Photo", storedPath);
        }
        return new PathResource(file);
    }

    public String contentType(String storedPath) {
        if (storedPath.endsWith(".png")) return "image/png";
        if (storedPath.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    /** Removes a photo whose ticket could not be saved. */
    public void delete(String storedPath) {
        if (storedPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String storedPath) {
        Path file = root.resolve(storedPath).normalize();
        if (!file.startsWith(root)) {
            throw new NotFoundException("Photo", storedPath);
        }
        return file;
    }

    private String extensionOf(MultipartFile photo) throws IOException {
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

    private boolean startsWith(byte[] bytes, int offset, int... expected) {
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
