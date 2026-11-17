package orochi.steelsight.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Where photos are kept. Callers check permissions first, because this layer has no idea who is asking. */
public interface PhotoStorage {

    /**
     * The largest photo anyone may upload. The servlet container allows bigger uploads, so a photo
     * over this size reaches the page and the form can say what's wrong instead of failing outright.
     * The forms' file inputs repeat this number as data-max-bytes.
     */
    long MAX_BYTES = 5L * 1024 * 1024;

    /** Returns the stored name, or null when no photo was chosen. */
    String store(PhotoAlbum album, MultipartFile photo);

    Resource load(PhotoAlbum album, String storedName);

    /** Removes a photo that is no longer used. Doing nothing for a name that isn't there is fine. */
    void delete(PhotoAlbum album, String storedName);

    default String contentType(String storedName) {
        if (storedName.endsWith(".png")) return "image/png";
        if (storedName.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
