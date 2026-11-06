package orochi.steelsight.web;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewAccountForm {
    private String employeeCode;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String roleName;
    private Long teamId;
    private String password;
}
