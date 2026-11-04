package orochi.steelsight.identity.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.SoftDeletableModel;
import orochi.steelsight.scheduling.model.Team;

@Getter
@Setter
@Entity
@Table(name = "app_user")
public class AppUser extends SoftDeletableModel {

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(unique = true)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "face_file_path")
    private String faceFilePath;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
