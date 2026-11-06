package orochi.steelsight.ppe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;

@Getter
@Setter
@Entity
@Table(name = "ppe_vest")
public class PpeVest extends IdentifiedModel {

    @Column(name = "vest_code", nullable = false, unique = true)
    private String vestCode;
}
