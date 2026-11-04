package orochi.steelsight.scheduling.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;

@Getter
@Setter
@Entity
@Table(name = "zone")
public class Zone extends IdentifiedModel {

    @Column(nullable = false, unique = true)
    private String name;
}
