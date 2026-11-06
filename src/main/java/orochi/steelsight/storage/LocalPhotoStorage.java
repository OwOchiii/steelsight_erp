package orochi.steelsight.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import orochi.steelsight.exception.NotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Keeps ticket photos on local disk, for development and tests. Stored names are random.
 */
@Component
@ConditionalOnProperty(name = "steelsight.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage {

    private final Path root;

    public LocalPhotoStorage(@Value("${steelsight.storage.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        try {
            String extension = PhotoFormat.extensionOf(photo);
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

    @Override
    public Resource load(String storedPath) {
        Path file = resolve(storedPath);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("Photo", storedPath);
        }
        return new PathResource(file);
    }

    @Override
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
}
