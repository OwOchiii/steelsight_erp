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
 * Keeps photos on local disk, for development and tests. Ticket photos stay at the top of the folder,
 * where earlier versions put them, and face photos go in faces/. Stored names are random.
 */
@Component
@ConditionalOnProperty(name = "steelsight.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage {

    private final Path root;

    public LocalPhotoStorage(@Value("${steelsight.storage.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public String store(PhotoAlbum album, MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        try {
            String name = UUID.randomUUID() + PhotoFormat.extensionOf(photo);
            Path folder = folder(album);
            Files.createDirectories(folder);
            try (InputStream in = photo.getInputStream()) {
                Files.copy(in, folder.resolve(name));
            }
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Resource load(PhotoAlbum album, String storedName) {
        Path file = resolve(album, storedName);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("Photo", storedName);
        }
        return new PathResource(file);
    }

    @Override
    public void delete(PhotoAlbum album, String storedName) {
        if (!PhotoFormat.isStoredName(storedName)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(album, storedName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path folder(PhotoAlbum album) {
        return album == PhotoAlbum.FACE ? root.resolve("faces") : root;
    }

    private Path resolve(PhotoAlbum album, String storedName) {
        if (!PhotoFormat.isStoredName(storedName)) {
            throw new NotFoundException("Photo", storedName);
        }
        Path folder = folder(album);
        Path file = folder.resolve(storedName).normalize();
        if (!file.getParent().equals(folder)) {
            throw new NotFoundException("Photo", storedName);
        }
        return file;
    }
}
