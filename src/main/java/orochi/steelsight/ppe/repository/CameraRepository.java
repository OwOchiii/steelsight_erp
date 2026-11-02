package orochi.steelsight.ppe.repository;

import orochi.steelsight.ppe.model.Camera;
import orochi.steelsight.repository.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface CameraRepository extends BaseRepository<Camera, Long> {

    Optional<Camera> findByCameraCode(String cameraCode);

    List<Camera> findAllByZoneId(Long zoneId);
}
