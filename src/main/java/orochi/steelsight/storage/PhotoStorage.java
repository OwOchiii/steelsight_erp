package orochi.steelsight.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Where ticket photos are kept. Callers check permissions first, because this layer has no idea who is asking. */
public interface PhotoStorage {

    /** Returns the stored path, or null when no photo was chosen. */
    String store(MultipartFile photo);

    Resource load(String storedPath);

    /** Removes a photo whose ticket could not be saved. */
    void delete(String storedPath);

    default String contentType(String storedPath) {
        if (storedPath.endsWith(".png")) return "image/png";
        if (storedPath.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
