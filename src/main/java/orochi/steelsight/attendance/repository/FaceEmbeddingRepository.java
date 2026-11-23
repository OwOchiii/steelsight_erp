package orochi.steelsight.attendance.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.attendance.model.EmbeddingStatus;
import orochi.steelsight.attendance.model.FaceEmbedding;
import orochi.steelsight.attendance.model.FacePose;
import orochi.steelsight.repository.BaseRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FaceEmbeddingRepository extends BaseRepository<FaceEmbedding, Long> {

    Optional<FaceEmbedding> findByUserIdAndPose(Long userId, FacePose pose);

    /** The worker's photo taken straight on, the one on their account. */
    default Optional<FaceEmbedding> findFrontByUserId(Long userId) {
        return findByUserIdAndPose(userId, FacePose.FRONT);
    }

    List<FaceEmbedding> findAllByUserIdOrderByPose(Long userId);

    List<FaceEmbedding> findAllByStatus(EmbeddingStatus status);

    List<FaceEmbedding> findAllByUserIdInAndPose(Collection<Long> userIds, FacePose pose);

    /**
     * Queues one angle of a worker's face for enrolment as one atomic statement. A new photo clears
     * that angle's old vector, so a worker is never matched against a face they have replaced.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO face_embedding (user_id, pose, source_file_path)
            VALUES (:userId, :pose, :sourceFilePath)
            ON CONFLICT (user_id, pose) DO UPDATE
            SET source_file_path = EXCLUDED.source_file_path,
                status = 'PENDING',
                embedding = NULL,
                model_name = NULL,
                error_message = NULL
            """)
    int queueForEnrolment(@Param("userId") Long userId, @Param("pose") String pose,
                          @Param("sourceFilePath") String sourceFilePath);

    /** Queues the FRONT photo, the one on the account. */
    default int queueForEnrolment(Long userId, String sourceFilePath) {
        return queueForEnrolment(userId, FacePose.FRONT.name(), sourceFilePath);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = "DELETE FROM face_embedding WHERE user_id = :userId AND pose = :pose")
    int deleteAngle(@Param("userId") Long userId, @Param("pose") String pose);

    /** Face photos the detection app could not use, of people not deleted (SRS 3.2.3). */
    @org.springframework.data.jpa.repository.Query("SELECT count(f) FROM FaceEmbedding f WHERE f.status = orochi.steelsight.attendance.model.EmbeddingStatus.FAILED AND f.user.deletedAt IS NULL")
    long countFailed();
}
