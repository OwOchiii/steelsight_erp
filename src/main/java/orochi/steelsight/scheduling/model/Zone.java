package orochi.steelsight.scheduling.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "zone")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;
}
