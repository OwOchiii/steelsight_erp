package orochi.steelsight.identity.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;

@Getter
@Setter
@Entity
@Table(name = "permission")
public class Permission extends IdentifiedModel {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String module;

    @Column(nullable = false)
    private String action;

    private String description;
}
