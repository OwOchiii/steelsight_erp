package orochi.steelsight.attendance.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.attendance.model.EmbeddingStatus;
import orochi.steelsight.attendance.model.FaceEmbedding;
import orochi.steelsight.repository.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface FaceEmbeddingRepository extends BaseRepository<FaceEmbedding, Long> {

    Optional<FaceEmbedding> findByUserId(Long userId);

    List<FaceEmbedding> findAllByStatus(EmbeddingStatus status);

    /**
     * Queues a worker's photo for enrolment as one atomic statement. A new photo clears the old
     * vector, so a worker is never matched against a face they have replaced.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO face_embedding (user_id, source_file_path)
            VALUES (:userId, :sourceFilePath)
            ON CONFLICT (user_id) DO UPDATE
            SET source_file_path = EXCLUDED.source_file_path,
                status = 'PENDING',
                embedding = NULL,
                model_name = NULL,
                error_message = NULL
            """)
    int queueForEnrolment(@Param("userId") Long userId, @Param("sourceFilePath") String sourceFilePath);
}
