package orochi.steelsight.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import orochi.steelsight.exception.NotFoundException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPhotoStorageTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    @TempDir Path dir;

    @Test
    void ticketPhotosStayAtTheTopAndFacePhotosGoInTheirOwnFolder() {
        LocalPhotoStorage storage = new LocalPhotoStorage(dir.toString());

        String ticket = storage.store(PhotoAlbum.TICKET, new MockMultipartFile("photo", JPEG));
        String face = storage.store(PhotoAlbum.FACE, new MockMultipartFile("photo", JPEG));

        assertThat(dir.resolve(ticket)).exists();
        assertThat(dir.resolve("faces").resolve(face)).exists();
    }

    @Test
    void oneAlbumCannotReadAnothersPhotos() {
        LocalPhotoStorage storage = new LocalPhotoStorage(dir.toString());
        String face = storage.store(PhotoAlbum.FACE, new MockMultipartFile("photo", JPEG));

        assertThatThrownBy(() -> storage.load(PhotoAlbum.TICKET, face)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> storage.load(PhotoAlbum.TICKET, "faces/" + face)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> storage.load(PhotoAlbum.FACE, "../" + face)).isInstanceOf(NotFoundException.class);
        assertThat(storage.load(PhotoAlbum.FACE, face).exists()).isTrue();
    }

    @Test
    void deleteRemovesOnlyThatPhotoAndIgnoresBadNames() throws Exception {
        LocalPhotoStorage storage = new LocalPhotoStorage(dir.toString());
        String keep = storage.store(PhotoAlbum.FACE, new MockMultipartFile("photo", JPEG));
        String gone = storage.store(PhotoAlbum.FACE, new MockMultipartFile("photo", JPEG));
        Files.writeString(dir.resolve("outside.txt"), "keep me");

        storage.delete(PhotoAlbum.FACE, gone);
        storage.delete(PhotoAlbum.FACE, "../outside.txt");
        storage.delete(PhotoAlbum.FACE, null);

        assertThat(dir.resolve("faces").resolve(gone)).doesNotExist();
        assertThat(dir.resolve("faces").resolve(keep)).exists();
        assertThat(dir.resolve("outside.txt")).exists();
    }
}
