package orochi.steelsight.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import orochi.steelsight.exception.NotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Keeps photos in private Supabase Storage buckets, one per album. The server key stays on the server
 * and browsers only ever get photos through the ERP's pages, so their permission checks still apply.
 */
@Component
@ConditionalOnProperty(name = "steelsight.storage.type", havingValue = "supabase")
public class SupabasePhotoStorage implements PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabasePhotoStorage.class);
    private static final String OBJECT = "/storage/v1/object/{bucket}/{name}";

    private final RestClient client;
    private final String ticketBucket;
    private final String faceBucket;
    private final String evidenceBucket;

    @Autowired
    public SupabasePhotoStorage(RestClient.Builder builder,
                                @Value("${steelsight.storage.supabase-url:}") String url,
                                @Value("${steelsight.storage.supabase-key:}") String key,
                                @Value("${steelsight.storage.supabase-bucket:ticket-photos}") String ticketBucket,
                                @Value("${steelsight.storage.supabase-face-bucket:face-photos}") String faceBucket,
                                @Value("${steelsight.storage.supabase-evidence-bucket:evidence-photos}") String evidenceBucket) {
        this(configured(builder.requestFactory(timeouts()), url, key), ticketBucket, faceBucket, evidenceBucket);
    }

    SupabasePhotoStorage(RestClient client, String ticketBucket, String faceBucket) {
        this(client, ticketBucket, faceBucket, "evidence-photos");
    }

    SupabasePhotoStorage(RestClient client, String ticketBucket, String faceBucket, String evidenceBucket) {
        if (ticketBucket.equals(faceBucket)) {
            throw new IllegalStateException("Face photos need their own bucket, apart from ticket photos");
        }
        this.client = client;
        this.ticketBucket = ticketBucket;
        this.faceBucket = faceBucket;
        this.evidenceBucket = evidenceBucket;
    }

    /** Also used by tests, so they exercise the same URL and headers as the real thing. */
    static RestClient configured(RestClient.Builder builder, String url, String key) {
        if (url.isBlank() || key.isBlank()) {
            throw new IllegalStateException("Supabase storage needs SUPABASE_URL and SUPABASE_SERVICE_KEY");
        }
        return builder
                .baseUrl(url.replaceAll("/+$", ""))
                .defaultHeader("apikey", key)
                .defaultHeader("Authorization", "Bearer " + key)
                .build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    @Override
    public String store(PhotoAlbum album, MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        try {
            String name = UUID.randomUUID() + PhotoFormat.extensionOf(photo);
            if (album == PhotoAlbum.FACE) {
                PhotoFormat.checkFace(photo);
            }
            client.post().uri(OBJECT, bucket(album), name)
                    .contentType(MediaType.parseMediaType(contentType(name)))
                    .body(photo.getBytes())
                    .retrieve()
                    .toBodilessEntity();
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Resource load(PhotoAlbum album, String storedPath) {
        if (!PhotoFormat.isAllowed(album, storedPath)) {
            throw new NotFoundException("Photo", storedPath);
        }
        try {
            // Built by path so that the folders of an evidence path stay folders, not %2F.
            byte[] bytes = client.get().uri(uri -> uri.path("/storage/v1/object/" + bucket(album) + "/").path(storedPath).build())
                    .retrieve().body(byte[].class);
            if (bytes == null) {
                throw new NotFoundException("Photo", storedPath);
            }
            return new ByteArrayResource(bytes);
        } catch (RestClientResponseException e) {
            // Supabase Storage can answer 400 for an object that doesn't exist.
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404) {
                throw new NotFoundException("Photo", storedPath);
            }
            throw e;
        }
    }

    /** A cleanup, so a failure here must not hide the original error or undo a saved change. */
    @Override
    public void delete(PhotoAlbum album, String storedPath) {
        if (!PhotoFormat.isStoredName(storedPath)) {
            return;
        }
        try {
            client.delete().uri(OBJECT, bucket(album), storedPath).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not remove photo {}: {}", storedPath, e.getClass().getSimpleName());
        }
    }

    private String bucket(PhotoAlbum album) {
        return switch (album) {
            case FACE -> faceBucket;
            case EVIDENCE -> evidenceBucket;
            case TICKET -> ticketBucket;
        };
    }
}
