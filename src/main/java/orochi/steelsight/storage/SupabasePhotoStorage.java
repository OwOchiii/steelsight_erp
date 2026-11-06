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
 * Keeps ticket photos in a private Supabase Storage bucket. The server key stays on the server and
 * browsers only ever get photos through the ticket page, so its permission checks still apply.
 */
@Component
@ConditionalOnProperty(name = "steelsight.storage.type", havingValue = "supabase")
public class SupabasePhotoStorage implements PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabasePhotoStorage.class);
    private static final String OBJECT = "/storage/v1/object/{bucket}/{name}";

    private final RestClient client;
    private final String bucket;

    @Autowired
    public SupabasePhotoStorage(RestClient.Builder builder,
                                @Value("${steelsight.storage.supabase-url:}") String url,
                                @Value("${steelsight.storage.supabase-key:}") String key,
                                @Value("${steelsight.storage.supabase-bucket:ticket-photos}") String bucket) {
        this(configured(builder.requestFactory(timeouts()), url, key), bucket);
    }

    SupabasePhotoStorage(RestClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
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
    public String store(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        try {
            String name = UUID.randomUUID() + PhotoFormat.extensionOf(photo);
            client.post().uri(OBJECT, bucket, name)
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
    public Resource load(String storedPath) {
        try {
            byte[] bytes = client.get().uri(OBJECT, bucket, storedPath).retrieve().body(byte[].class);
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

    /** Only a cleanup after a failed save, so a failure here must not hide the original error. */
    @Override
    public void delete(String storedPath) {
        if (storedPath == null) {
            return;
        }
        try {
            client.delete().uri(OBJECT, bucket, storedPath).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not remove photo {}: {}", storedPath, e.getClass().getSimpleName());
        }
    }
}
