package orochi.steelsight.identity.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String module;

    @Column(nullable = false)
    private String action;

    private String description;
}
