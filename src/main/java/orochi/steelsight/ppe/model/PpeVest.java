package orochi.steelsight.ppe.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "ppe_vest")
public class PpeVest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vest_code", nullable = false, unique = true)
    private String vestCode;
}
