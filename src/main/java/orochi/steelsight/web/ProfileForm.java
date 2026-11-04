package orochi.steelsight.web;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileForm {
    private String fullName;
    private String email;
    private String phoneNumber;
    private long version;
}
