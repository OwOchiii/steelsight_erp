package orochi.steelsight.attendance.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.BaseModel;

/**
 * One face photo of a worker in the enrolment queue: one row per worker and angle (FacePose).
 * The ERP only queues photos; the detection service computes and matches the vectors, so the
 * embedding column is deliberately not mapped here.
 */
@Getter
@Setter
@Entity
@Table(name = "face_embedding")
public class FaceEmbedding extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FacePose pose = FacePose.FRONT;

    @Column(name = "source_file_path", nullable = false)
    private String sourceFilePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmbeddingStatus status = EmbeddingStatus.PENDING;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "error_message")
    private String errorMessage;
}
