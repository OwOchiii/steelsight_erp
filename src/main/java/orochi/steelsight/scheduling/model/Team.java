package orochi.steelsight.scheduling.model;

import jakarta.persistence.*;
import lombok.Data;
import orochi.steelsight.identity.model.AppUser;

@Data
@Entity
@Table(name = "team")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private AppUser manager;
}
