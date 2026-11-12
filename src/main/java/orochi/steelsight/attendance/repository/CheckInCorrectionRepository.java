package orochi.steelsight.attendance.repository;

import orochi.steelsight.attendance.model.CheckInCorrection;
import orochi.steelsight.repository.BaseRepository;

import java.util.List;

public interface CheckInCorrectionRepository extends BaseRepository<CheckInCorrection, Long> {

    List<CheckInCorrection> findAllByCheckInIdOrderByCorrectedAtDescIdDesc(Long checkInId);
}
